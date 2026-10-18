package com.amilingo.platform.module.lang.planner;

import com.amilingo.platform.module.lang.enums.Section;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 切片任务分配器（长周期规划引擎核心）：
 * 把 {@link TimeSlicer} 产出的工作切片与任务池模板匹配，严格受本周分科配额约束
 * （周配额由月度配额下放，因此任务总量不会突破月度上限）。
 *
 * <ul>
 *   <li>🟢 高效切片：优先 HIGH（套题/成文/精读精听），回落 MEDIUM。</li>
 *   <li>⚪ 普通切片：优先 MEDIUM，允许 HIGH 溢出与 LIGHT 填充。</li>
 *   <li>🟡 碎片切片：仅 LIGHT（背诵/泛听/跟读）。</li>
 *   <li>分科选择：配额剩余最多者优先，薄弱分科次序作为同分时的优先序。</li>
 * </ul>
 */
@Component
public class SliceAllocator {

    private final TaskPoolCatalog taskPool;

    public SliceAllocator(TaskPoolCatalog taskPool) {
        this.taskPool = taskPool;
    }

    /**
     * 分配一周任务。
     *
     * @param weekQuotaBySection 本周分科配额（分钟）
     * @param dayBudgets         每个自然日的分科分钟预算（各日预算之和严格等于周配额，
     *                           保证任务铺满整周而非堆积在周初；分配过程中会被消耗）
     * @return 分配结果（任务 + 实际排入分钟）
     */
    public Result allocate(Long userId, Long cycleId, String monthKey, LocalDate weekStart,
                           String stage, List<TimeSlicer.WorkSlice> slices,
                           List<Section> weak,
                           Map<Section, Integer> weekQuotaBySection,
                           Map<LocalDate, Map<Section, Integer>> dayBudgets) {

        Map<Section, Integer> weekRemaining = new EnumMap<>(Section.class);
        for (Section s : Section.values()) {
            weekRemaining.put(s, weekQuotaBySection.getOrDefault(s, 0));
        }
        Map<Section, Integer> rotation = new EnumMap<>(Section.class);
        List<Section> order = orderSections(weak);

        List<StudyTask> tasks = new ArrayList<>();
        for (TimeSlicer.WorkSlice slice : slices) {
            LocalDate day = slice.start().toLocalDate();
            Map<Section, Integer> dayRemaining = dayBudgets.getOrDefault(day, Map.of());

            Section chosen = null;
            TaskPoolCatalog.TaskEntry chosenEntry = null;
            int chosenMinutes = 0;

            // 按当日剩余预算从多到少挑分科（薄弱优先序同分决胜）
            List<Section> ranked = new ArrayList<>(order);
            ranked.sort((a, b) -> {
                int cmp = Integer.compare(dayRemaining.getOrDefault(b, 0), dayRemaining.getOrDefault(a, 0));
                return cmp != 0 ? cmp : Integer.compare(order.indexOf(a), order.indexOf(b));
            });

            int sliceMinutes = slice.minutes();

            // 第一轮：找一个「模板能完整放下」的分科（优先保住 50/45/30 分钟整块学习节奏）
            for (Section section : ranked) {
                int cap = Math.min(sliceMinutes,
                        Math.min(dayRemaining.getOrDefault(section, 0), weekRemaining.getOrDefault(section, 0)));
                if (cap < 10) continue;
                List<TaskPoolCatalog.TaskEntry> candidates = taskPool.candidates(section, slice.kind());
                int rot = rotation.getOrDefault(section, 0);
                for (int k = 0; k < candidates.size(); k++) {
                    TaskPoolCatalog.TaskEntry entry = candidates.get((rot + k) % candidates.size());
                    if (entry.minutes() <= cap) {
                        chosen = section;
                        chosenEntry = entry;
                        chosenMinutes = entry.minutes();
                        rotation.put(section, (rot + k + 1) % Math.max(1, candidates.size()));
                        break;
                    }
                }
                if (chosen != null) break;
            }

            // 第二轮：所有分科都放不下完整模板时，用最短模板按剩余预算截断填充（尾段利用，绝不超额）
            if (chosen == null) {
                for (Section section : ranked) {
                    int cap = Math.min(sliceMinutes,
                            Math.min(dayRemaining.getOrDefault(section, 0), weekRemaining.getOrDefault(section, 0)));
                    if (cap < 10) continue;
                    TaskPoolCatalog.TaskEntry shortest = taskPool.candidates(section, slice.kind()).stream()
                            .min(Comparator.comparingInt(TaskPoolCatalog.TaskEntry::minutes)).orElse(null);
                    if (shortest != null) {
                        chosen = section;
                        chosenEntry = shortest;
                        chosenMinutes = Math.min(shortest.minutes(), cap);
                        break;
                    }
                }
            }

            if (chosen == null) {
                continue; // 当日配额已耗尽或碎片切片无轻量任务可放：保持空闲，绝不突破月度上限
            }

            int minutes = chosenMinutes;
            // 消耗日预算（可变 Map）与周剩余
            dayRemaining.merge(chosen, -minutes, Integer::sum);
            weekRemaining.merge(chosen, -minutes, Integer::sum);
            tasks.add(StudyTask.builder()
                    .userId(userId)
                    .planId(cycleId)
                    .cycleId(cycleId)
                    .monthKey(monthKey)
                    .weekStart(weekStart)
                    .section(chosen)
                    .taskType(chosenEntry.taskType())
                    .title(chosenEntry.title())
                    .description(chosenEntry.objective())
                    .objective(chosenEntry.objective())
                    .scheduledStart(slice.start())
                    .scheduledEnd(slice.start().plusMinutes(minutes))
                    .durationMinutes(minutes)
                    .stage(stage)
                    .slotType(slice.kind().name())
                    .difficulty(chosenEntry.difficulty().name())
                    .done(false)
                    .progressStatus("TODO")
                    .build());
        }

        tasks.sort((a, b) -> a.getScheduledStart().compareTo(b.getScheduledStart()));
        int allocated = tasks.stream().mapToInt(StudyTask::getDurationMinutes).sum();
        Map<Section, Integer> used = new EnumMap<>(Section.class);
        for (StudyTask t : tasks) {
            used.merge(t.getSection(), t.getDurationMinutes(), Integer::sum);
        }
        return new Result(tasks, allocated, used);
    }

    /** 薄弱优先，其余分科去重后按固定顺序补齐。 */
    private static List<Section> orderSections(List<Section> weak) {
        Set<Section> ordered = new LinkedHashSet<>();
        if (weak != null) {
            ordered.addAll(weak);
        }
        ordered.add(Section.LISTENING);
        ordered.add(Section.READING);
        ordered.add(Section.WRITING);
        ordered.add(Section.SPEAKING);
        return new ArrayList<>(ordered);
    }

    /**
     * @param tasks           已排任务（按时间排序）
     * @param allocatedMinutes 实际排入总分钟
     * @param usedBySection   各科实际排入分钟
     */
    public record Result(List<StudyTask> tasks, int allocatedMinutes, Map<Section, Integer> usedBySection) {
    }
}
