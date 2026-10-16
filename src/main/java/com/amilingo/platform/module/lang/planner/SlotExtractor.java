package com.amilingo.platform.module.lang.planner;

import com.amilingo.platform.module.lang.entity.ScheduleBlock;
import com.amilingo.platform.module.lang.repository.ScheduleBlockRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 时间槽提取器（PlanGenerator Step 2）：在默认学习时段（08:00–22:00）内，
 * 扣除时间资源中的占用块（{@link ScheduleBlock}：UNAVAILABLE/FRAGMENT/REST），
 * 得到连续空闲时间槽，并过滤掉短于 30 分钟的碎片。
 * DEEP/FREE 为用户显式标记的可排程时段，不做扣除。
 */
@Component
public class SlotExtractor {

    /** 一天内可安排学习的最早时间。 */
    private static final LocalTime DAY_START = LocalTime.of(8, 0);
    /** 一天内可安排学习的最晚时间。 */
    private static final LocalTime DAY_END = LocalTime.of(22, 0);
    /** 小于该时长的空闲槽视为碎片，丢弃。 */
    private static final long MIN_SLOT_MINUTES = 30;

    private final ScheduleBlockRepository scheduleBlockRepository;

    public SlotExtractor(ScheduleBlockRepository scheduleBlockRepository) {
        this.scheduleBlockRepository = scheduleBlockRepository;
    }

    /** 提取某一周 [weekStart, weekEnd]（含端点）的空闲时间槽。 */
    public List<TimeSlot> extract(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        List<TimeSlot> slots = new ArrayList<>();
        LocalDate cursor = weekStart;
        while (!cursor.isAfter(weekEnd)) {
            slots.addAll(extractDay(userId, cursor));
            cursor = cursor.plusDays(1);
        }
        return slots;
    }

    /* ==================== 长周期规划引擎：24h 类型化提取 ==================== */

    /** 未显式标记的时间，仅在默认学习窗口内视为 ⚪ 普通空闲（避免凌晨睡眠时间被算作可用）。 */
    private static final LocalTime DEFAULT_WINDOW_START = LocalTime.of(7, 0);
    private static final LocalTime DEFAULT_WINDOW_END = LocalTime.of(23, 30);
    /** 🟢/⚪ 槽最短分钟数；🟡 碎片槽最短分钟数（轻量任务可短至 10 分钟）。 */
    private static final long MIN_DEEP_FREE_MINUTES = 30;
    private static final long MIN_FRAGMENT_MINUTES = 10;

    /** 提取日期区间内（含端点）的类型化时间槽，按开始时间排序。 */
    public List<TypedTimeSlot> extractTyped(Long userId, LocalDate from, LocalDate to) {
        List<TypedTimeSlot> slots = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            slots.addAll(extractTypedDay(userId, cursor));
            cursor = cursor.plusDays(1);
        }
        slots.sort(Comparator.comparing(TypedTimeSlot::start));
        return slots;
    }

    private List<TypedTimeSlot> extractTypedDay(Long userId, LocalDate date) {
        int dayOfWeek = date.getDayOfWeek().getValue();
        List<ScheduleBlock> blocks = scheduleBlockRepository.findByUserIdAndDayOfWeek(userId, dayOfWeek);

        // 分钟级类型掩码：null = 不可排程
        TypedTimeSlot.Kind[] mask = new TypedTimeSlot.Kind[1440];
        int winS = toMinutes(DEFAULT_WINDOW_START);
        int winE = toMinutes(DEFAULT_WINDOW_END);
        for (int m = winS; m < winE; m++) {
            mask[m] = TypedTimeSlot.Kind.FREE;
        }

        // 先应用周循环，再应用一次性事件（一次性事件优先覆盖）
        List<ScheduleBlock> weekly = new ArrayList<>();
        List<ScheduleBlock> once = new ArrayList<>();
        for (ScheduleBlock b : blocks) {
            if (!isActiveOn(b, date)) continue;
            if (b.getRecurrence() == ScheduleBlock.RecurrenceType.ONCE) once.add(b);
            else weekly.add(b);
        }
        weekly.forEach(b -> paintBlock(mask, b));
        once.forEach(b -> paintBlock(mask, b));

        // 合并同类型连续分钟段
        List<TypedTimeSlot> result = new ArrayList<>();
        int start = -1;
        TypedTimeSlot.Kind cur = null;
        for (int m = 0; m <= 1440; m++) {
            TypedTimeSlot.Kind k = m < 1440 ? mask[m] : null;
            if (k != cur) {
                if (cur != null) {
                    long len = m - start;
                    long minLen = cur == TypedTimeSlot.Kind.FRAGMENT ? MIN_FRAGMENT_MINUTES : MIN_DEEP_FREE_MINUTES;
                    if (len >= minLen) {
                        result.add(new TypedTimeSlot(
                                LocalDateTime.of(date, toTime(start)),
                                LocalDateTime.of(date, toTime(m)),
                                cur));
                    }
                }
                start = m;
                cur = k;
            }
        }
        return result;
    }

    /** 将一个时间块涂到掩码上：🔴/🟣 清空，其余按类型着色（块本身已保证不跨午夜）。 */
    private static void paintBlock(TypedTimeSlot.Kind[] mask, ScheduleBlock block) {
        int s = toMinutes(block.getStartTime());
        int e = toMinutes(block.getEndTime());
        TypedTimeSlot.Kind kind = switch (block.getBlockType()) {
            case DEEP -> TypedTimeSlot.Kind.DEEP;
            case FRAGMENT -> TypedTimeSlot.Kind.FRAGMENT;
            case FREE -> TypedTimeSlot.Kind.FREE;
            default -> null; // UNAVAILABLE / REST
        };
        for (int m = Math.max(0, s); m < Math.min(1440, e); m++) {
            mask[m] = kind;
        }
    }

    /** 块在指定日期是否生效（周循环按生效日期范围，一次性按具体日期）。 */
    private static boolean isActiveOn(ScheduleBlock block, LocalDate date) {
        if (block.getRecurrence() == ScheduleBlock.RecurrenceType.ONCE) {
            return date.equals(block.getSpecificDate());
        }
        if (block.getEffectiveFrom() != null && date.isBefore(block.getEffectiveFrom())) return false;
        if (block.getEffectiveTo() != null && date.isAfter(block.getEffectiveTo())) return false;
        return true;
    }

    private List<TimeSlot> extractDay(Long userId, LocalDate date) {
        int dayOfWeek = date.getDayOfWeek().getValue();
        List<ScheduleBlock> blocks = scheduleBlockRepository.findByUserIdAndDayOfWeek(userId, dayOfWeek);

        // 占用区间（分钟），裁剪到学习窗口内
        List<int[]> occupied = new ArrayList<>();
        for (ScheduleBlock block : blocks) {
            if (!isOccupiedOn(block, date)) continue;
            int s = Math.max(toMinutes(DAY_START), toMinutes(block.getStartTime()));
            int e = Math.min(toMinutes(DAY_END), toMinutes(block.getEndTime()));
            if (e > s) {
                occupied.add(new int[]{s, e});
            }
        }
        occupied.sort(Comparator.comparingInt(a -> a[0]));

        // 合并重叠/相邻占用区间
        List<int[]> merged = new ArrayList<>();
        for (int[] interval : occupied) {
            if (merged.isEmpty() || interval[0] > merged.get(merged.size() - 1)[1]) {
                merged.add(new int[]{interval[0], interval[1]});
            } else {
                merged.get(merged.size() - 1)[1] = Math.max(merged.get(merged.size() - 1)[1], interval[1]);
            }
        }

        // 窗口减去占用，得到空闲
        List<TimeSlot> free = new ArrayList<>();
        int cursor = toMinutes(DAY_START);
        for (int[] interval : merged) {
            addSlotIfLongEnough(free, date, cursor, interval[0]);
            cursor = Math.max(cursor, interval[1]);
        }
        addSlotIfLongEnough(free, date, cursor, toMinutes(DAY_END));
        return free;
    }

    /** 该时间块在指定日期是否构成「不可排程」占用。 */
    private static boolean isOccupiedOn(ScheduleBlock block, LocalDate date) {
        // DEEP/FREE 是用户显式标记的可排程时段，不扣除
        if (block.getBlockType() == ScheduleBlock.BlockType.DEEP
                || block.getBlockType() == ScheduleBlock.BlockType.FREE) {
            return false;
        }
        // 一次性事件只在具体日期生效
        if (block.getRecurrence() == ScheduleBlock.RecurrenceType.ONCE) {
            return date.equals(block.getSpecificDate());
        }
        // 周循环：按生效日期范围过滤
        if (block.getEffectiveFrom() != null && date.isBefore(block.getEffectiveFrom())) return false;
        if (block.getEffectiveTo() != null && date.isAfter(block.getEffectiveTo())) return false;
        return true;
    }

    private static void addSlotIfLongEnough(List<TimeSlot> slots, LocalDate date, int startMin, int endMin) {
        if (endMin - startMin < MIN_SLOT_MINUTES) {
            return;
        }
        LocalDateTime start = LocalDateTime.of(date, toTime(startMin));
        LocalDateTime end = LocalDateTime.of(date, toTime(endMin));
        slots.add(new TimeSlot(start, end));
    }

    private static int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static LocalTime toTime(int minutes) {
        return LocalTime.of(minutes / 60, minutes % 60);
    }
}
