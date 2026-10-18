package com.amilingo.platform.module.lang.planner;

import com.amilingo.platform.module.lang.repository.StudyTaskRepository;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 槽位匹配迁移器（自动/手动调整共用）。
 *
 * <p>替代旧的「同时刻平移」：平移会把任务送进用户已占用的上课/休息时间。
 * 本迁移器从指定日期起逐日向后扫描（本周剩余 → 下周），复用 {@link SlotExtractor#extractTyped}
 * 提取类型化时间槽，并按以下规则为每个逾期任务找最早的合格落点：
 * <ol>
 *   <li>难度匹配：HIGH 只进 🟢DEEP；MEDIUM 进 🟢/⚪；LIGHT 可进 🟡碎片（规则同 TaskPoolCatalog.allowed）。</li>
 *   <li>槽长 ≥ 任务时长，且扣除当日已有任务/本批已安置任务后仍有连续空闲段。</li>
 *   <li>迁入后当日总学习量不超过该日类型化槽总容量（日预算）。</li>
 *   <li>迁入后当日 HIGH 任务总量 ≤ {@link #HIGH_INTENSITY_DAILY_CAP} 分钟。</li>
 *   <li>下周迁入总量受下周配额 20% 上限约束（防债务雪球）。</li>
 * </ol>
 * 找不到合格槽的任务保留在 {@link Result#unresolvedTaskIds()}，绝不硬塞，交由上层走债务/降档级联。
 */
@Component
public class SlotMigrationPlanner {

    /** 单日高强度任务上限（与 ProgressAdjuster 保持一致）。 */
    public static final int HIGH_INTENSITY_DAILY_CAP = 240;
    /** 搜索窗口：扫描未来 14 天（覆盖本周剩余 + 下周）。 */
    public static final int DEFAULT_SEARCH_DAYS = 14;
    /** 债务结转上限：迁入下周的分钟数不得超过下周配额的 20%。 */
    public static final double NEXT_WEEK_DEBT_RATIO = 0.20;

    private final SlotExtractor slotExtractor;
    private final StudyTaskRepository taskRepository;

    public SlotMigrationPlanner(SlotExtractor slotExtractor, StudyTaskRepository taskRepository) {
        this.slotExtractor = slotExtractor;
        this.taskRepository = taskRepository;
    }

    /** 单条迁移落点。 */
    public record Move(Long taskId, LocalDateTime newStart, LocalDateTime newEnd,
                       LocalDate newWeekStart, String slotKind, String difficulty) {
    }

    /** 迁移计划结果。nextWeekDebtMinutes 为实际迁入下周的分钟数；unresolved 为无处可去的任务。 */
    public record Result(List<Move> moves, List<Long> unresolvedTaskIds, int nextWeekDebtMinutes) {

        public static Result empty() {
            return new Result(List.of(), List.of(), 0);
        }
    }

    /**
     * 为一批任务规划迁移落点。按天外循环（每天只提取一次槽），任务按时长降序在当天竞争落点。
     *
     * @param userId                  用户
     * @param cycleId                 周期
     * @param movingTasks             待迁移任务（逾期未完成）
     * @param searchFrom              扫描起始日（通常为明天）
     * @param searchDays              扫描天数
     * @param nextWeekDebtCapMinutes  迁入下周的分钟数上限；≤0 表示不允许迁入下周
     */
    public Result plan(Long userId, Long cycleId, List<StudyTask> movingTasks,
                       LocalDate searchFrom, int searchDays, int nextWeekDebtCapMinutes) {
        if (movingTasks == null || movingTasks.isEmpty()) {
            return Result.empty();
        }
        Set<Long> movingIds = new HashSet<>();
        movingTasks.forEach(t -> movingIds.add(t.getId()));

        // 周期内其余任务全部视为物理占用（已完成任务同样占用该时间段）
        List<StudyTask> occupied = taskRepository.findByCycleIdOrderByScheduledStart(cycleId).stream()
                .filter(t -> !movingIds.contains(t.getId()))
                .toList();

        LocalDate windowEnd = searchFrom.plusDays(searchDays - 1L);
        LocalDate nextWeekStart = weekStart(searchFrom).plusWeeks(1);

        // 未安置任务（大任务优先，降低长槽被小任务切碎后大任务无处可去的概率）
        List<StudyTask> pending = new ArrayList<>(movingTasks);
        pending.sort(Comparator.comparingInt(StudyTask::getDurationMinutes).reversed());

        List<Move> moves = new ArrayList<>();
        int nextWeekDebt = 0;

        for (LocalDate day = searchFrom; !day.isAfter(windowEnd) && !pending.isEmpty(); day = day.plusDays(1)) {
            boolean nextWeek = !day.isBefore(nextWeekStart);
            DayContext ctx = DayContext.load(slotExtractor, userId, occupied, moves, day);
            List<StudyTask> stillPending = new ArrayList<>();
            for (StudyTask task : pending) {
                // 下周债务上限：已迁入下周分钟 + 本任务不得超过 cap
                if (nextWeek && nextWeekDebt + task.getDurationMinutes() > nextWeekDebtCapMinutes) {
                    stillPending.add(task);
                    continue;
                }
                LocalDateTime start = ctx.findStart(task);
                if (start == null) {
                    stillPending.add(task);
                    continue;
                }
                LocalDateTime end = start.plusMinutes(task.getDurationMinutes());
                String kind = ctx.kindAt(start);
                moves.add(new Move(task.getId(), start, end, weekStart(day), kind, task.getDifficulty()));
                ctx.occupy(start, end, task.getDifficulty());
                if (nextWeek) {
                    nextWeekDebt += task.getDurationMinutes();
                }
            }
            pending = stillPending;
        }

        List<Long> unresolved = pending.stream().map(StudyTask::getId).toList();
        return new Result(moves, unresolved, nextWeekDebt);
    }

    /** 便捷重载：默认 14 天窗口。 */
    public Result plan(Long userId, Long cycleId, List<StudyTask> movingTasks,
                       LocalDate searchFrom, int nextWeekDebtCapMinutes) {
        return plan(userId, cycleId, movingTasks, searchFrom, DEFAULT_SEARCH_DAYS, nextWeekDebtCapMinutes);
    }

    /** ISO 周一日期。 */
    public static LocalDate weekStart(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }

    /* ==================== 内部：单日落点上下文 ==================== */

    /**
     * 某一天的槽位与占用上下文（当天有效，跨天即重建）。
     * 空闲段为「类型化槽扣除已有/已安置任务后」的连续分钟段。
     */
    private static final class DayContext {
        private LocalDate day;
        private final List<Integer> segStart = new ArrayList<>();
        private final List<Integer> segEnd = new ArrayList<>();
        private final List<TypedTimeSlot.Kind> segKind = new ArrayList<>();
        private long capacityMinutes;
        private int plannedMinutes;
        private int highMinutes;

        static DayContext load(SlotExtractor extractor, Long userId,
                               List<StudyTask> occupied, List<Move> placed, LocalDate day) {
            DayContext ctx = new DayContext();
            ctx.day = day;
            List<TypedTimeSlot> slots = extractor.extractTyped(userId, day, day);

            List<int[]> busy = new ArrayList<>();
            int planned = 0;
            int high = 0;
            for (StudyTask t : occupied) {
                if (!t.getScheduledStart().toLocalDate().equals(day)) continue;
                int s = toMinutes(t.getScheduledStart());
                int e = toMinutes(t.getScheduledEnd());
                if (e > s) busy.add(new int[]{s, e});
                planned += t.getDurationMinutes();
                if ("HIGH".equals(t.getDifficulty())) high += t.getDurationMinutes();
            }
            for (Move m : placed) {
                if (!m.newStart().toLocalDate().equals(day)) continue;
                busy.add(new int[]{toMinutes(m.newStart()), toMinutes(m.newEnd())});
                planned += (int) Duration.between(m.newStart(), m.newEnd()).toMinutes();
                if ("HIGH".equals(m.difficulty())) high += (int) Duration.between(m.newStart(), m.newEnd()).toMinutes();
            }
            busy.sort(Comparator.comparingInt(a -> a[0]));

            long capacity = 0;
            for (TypedTimeSlot slot : slots) {
                capacity += slot.durationMinutes();
                int ss = toMinutes(slot.start());
                int se = toMinutes(slot.end());
                int cursor = ss;
                for (int[] b : busy) {
                    if (b[1] <= cursor) continue;
                    if (b[0] >= se) break;
                    if (b[0] > cursor) ctx.add(cursor, Math.min(b[0], se), slot.kind());
                    cursor = Math.max(cursor, b[1]);
                    if (cursor >= se) break;
                }
                if (cursor < se) ctx.add(cursor, se, slot.kind());
            }
            ctx.capacityMinutes = capacity;
            ctx.plannedMinutes = planned;
            ctx.highMinutes = high;
            return ctx;
        }

        private void add(int s, int e, TypedTimeSlot.Kind kind) {
            if (e <= s) return;
            segStart.add(s);
            segEnd.add(e);
            segKind.add(kind);
        }

        String kindAt(LocalDateTime start) {
            int m = toMinutes(start);
            for (int i = 0; i < segStart.size(); i++) {
                if (m >= segStart.get(i) && m < segEnd.get(i)) return segKind.get(i).name();
            }
            return null;
        }

        /** 在当天为任务找最早合格起点；找不到返回 null。 */
        LocalDateTime findStart(StudyTask task) {
            int need = task.getDurationMinutes();
            boolean isHigh = "HIGH".equals(task.getDifficulty());
            if (plannedMinutes + need > capacityMinutes) return null;
            if (isHigh && highMinutes + need > HIGH_INTENSITY_DAILY_CAP) return null;

            TaskPoolCatalog.Difficulty difficulty;
            try {
                difficulty = TaskPoolCatalog.Difficulty.valueOf(
                        task.getDifficulty() == null ? "MEDIUM" : task.getDifficulty().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                difficulty = TaskPoolCatalog.Difficulty.MEDIUM;
            }
            for (int i = 0; i < segStart.size(); i++) {
                if (!TaskPoolCatalog.allowed(difficulty, segKind.get(i))) continue;
                if (segEnd.get(i) - segStart.get(i) >= need) {
                    return LocalDateTime.of(day, toTime(segStart.get(i)));
                }
            }
            return null;
        }

        /** 任务在当天安置后占位：从空闲段头部切走 [start,end)，并累计日预算/高强度分钟。 */
        void occupy(LocalDateTime start, LocalDateTime end, String difficulty) {
            int ms = toMinutes(start);
            int me = toMinutes(end);
            int need = me - ms;
            plannedMinutes += need;
            if ("HIGH".equals(difficulty)) highMinutes += need;
            for (int i = 0; i < segStart.size(); i++) {
                int s = segStart.get(i);
                int e = segEnd.get(i);
                if (me <= s || ms >= e) continue;
                TypedTimeSlot.Kind kind = segKind.get(i);
                segStart.remove(i);
                segEnd.remove(i);
                segKind.remove(i);
                int insertAt = i;
                if (s < ms) {
                    segStart.add(insertAt, s);
                    segEnd.add(insertAt, ms);
                    segKind.add(insertAt, kind);
                    insertAt++;
                }
                if (me < e) {
                    segStart.add(insertAt, me);
                    segEnd.add(insertAt, e);
                    segKind.add(insertAt, kind);
                }
                i--;
            }
        }
    }

    private static int toMinutes(LocalDateTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    private static LocalTime toTime(int minutes) {
        return LocalTime.of(minutes / 60, minutes % 60);
    }
}
