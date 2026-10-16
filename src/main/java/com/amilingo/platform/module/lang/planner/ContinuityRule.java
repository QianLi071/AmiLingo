package com.amilingo.platform.module.lang.planner;

import com.amilingo.platform.module.lang.enums.Section;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 连续性规则（PlanGenerator Step 4）：同一分科不应连续 3 天出现。
 *
 * <p>检测违规后，尝试在相邻日期找到一个不同分科的任务进行整体内容对调（交换分科与标题等，
 * 时间槽不变），要求对调后双方都不产生新的连续 3 天；找不到合适对调则保留原状（尽力调整）。
 */
@Component
@Deprecated(since = "2026-09，旧版连续 3 天重排规则，长周期引擎不再使用，勿新增调用")
public class ContinuityRule {

    public List<StudyTask> adjust(List<StudyTask> tasks) {
        if (tasks.size() < 3) {
            return tasks;
        }

        Presence presence = new Presence(tasks);
        int maxAttempts = tasks.size();
        int attempts = 0;

        while (attempts++ < maxAttempts) {
            StudyTask violation = findViolation(presence);
            if (violation == null) {
                break;
            }
            LocalDate date = violation.getScheduledStart().toLocalDate();
            Section section = violation.getSection();

            StudyTask swapTarget = findSwapTarget(tasks, presence, violation, date, section);
            if (swapTarget == null) {
                // 无法对调，跳过该任务避免死循环：标记已处理
                presence.markUnresolvable(date, section);
                continue;
            }
            swapContent(violation, swapTarget);
            presence.rebuild(tasks);
        }
        return tasks;
    }

    private static StudyTask findViolation(Presence presence) {
        for (Map.Entry<Section, List<LocalDate>> e : presence.sectionDates().entrySet()) {
            List<LocalDate> dates = e.getValue();
            for (int i = 0; i + 2 < dates.size(); i++) {
                LocalDate d0 = dates.get(i);
                LocalDate d1 = dates.get(i + 1);
                LocalDate d2 = dates.get(i + 2);
                if (d1.equals(d0.plusDays(1)) && d2.equals(d1.plusDays(1))) {
                    StudyTask task = presence.firstTask(d2, e.getKey());
                    if (task != null && !presence.isUnresolvable(d2, e.getKey())) {
                        return task;
                    }
                }
            }
        }
        return null;
    }

    private static StudyTask findSwapTarget(List<StudyTask> tasks, Presence presence,
                                            StudyTask violation, LocalDate date, Section section) {
        List<StudyTask> candidates = new ArrayList<>(tasks);
        candidates.sort((a, b) -> {
            long da = Math.abs(java.time.Duration.between(
                    a.getScheduledStart(), violation.getScheduledStart()).toDays());
            long db = Math.abs(java.time.Duration.between(
                    b.getScheduledStart(), violation.getScheduledStart()).toDays());
            return Long.compare(da, db);
        });

        for (StudyTask candidate : candidates) {
            LocalDate candidateDate = candidate.getScheduledStart().toLocalDate();
            Section candidateSection = candidate.getSection();
            if (candidate == violation || candidateDate.equals(date) || candidateSection == section) {
                continue;
            }
            // 模拟对调后检查
            if (!wouldCreateRun(presence, candidateDate, section, candidate)
                    && !wouldCreateRun(presence, date, candidateSection, violation)) {
                return candidate;
            }
        }
        return null;
    }

    /** 判断把 sectionToPlace 放到 date（忽略 ignoreTask 占用）后是否会形成连续 3 天。 */
    private static boolean wouldCreateRun(Presence presence, LocalDate date, Section section,
                                          StudyTask ignoreTask) {
        return presence.hasSection(date.minusDays(2), section, ignoreTask)
                && presence.hasSection(date.minusDays(1), section, ignoreTask)
                || presence.hasSection(date.minusDays(1), section, ignoreTask)
                && presence.hasSection(date.plusDays(1), section, ignoreTask)
                || presence.hasSection(date.plusDays(1), section, ignoreTask)
                && presence.hasSection(date.plusDays(2), section, ignoreTask);
    }

    private static void swapContent(StudyTask a, StudyTask b) {
        Section sec = a.getSection();
        String title = a.getTitle();
        String desc = a.getDescription();
        String type = a.getTaskType();

        a.setSection(b.getSection());
        a.setTitle(b.getTitle());
        a.setDescription(b.getDescription());
        a.setTaskType(b.getTaskType());

        b.setSection(sec);
        b.setTitle(title);
        b.setDescription(desc);
        b.setTaskType(type);
    }

    /** 分科-日期在场索引。 */
    private static final class Presence {
        private Map<Section, List<LocalDate>> sectionDates = new HashMap<>();
        private Map<LocalDate, Map<Section, List<StudyTask>>> byDate = new HashMap<>();
        private final List<String> unresolvable = new ArrayList<>();

        private Presence(List<StudyTask> tasks) {
            rebuild(tasks);
        }

        private void rebuild(List<StudyTask> tasks) {
            sectionDates = new HashMap<>();
            byDate = new HashMap<>();
            for (StudyTask task : tasks) {
                LocalDate date = task.getScheduledStart().toLocalDate();
                byDate.computeIfAbsent(date, d -> new EnumMap<>(Section.class))
                        .computeIfAbsent(task.getSection(), s -> new ArrayList<>())
                        .add(task);
                sectionDates.computeIfAbsent(task.getSection(), s -> new ArrayList<>()).add(date);
            }
            // 去重排序
            for (Map.Entry<Section, List<LocalDate>> e : sectionDates.entrySet()) {
                sectionDates.put(e.getKey(), e.getValue().stream().distinct().sorted().toList());
            }
        }

        private Map<Section, List<LocalDate>> sectionDates() {
            return sectionDates;
        }

        private StudyTask firstTask(LocalDate date, Section section) {
            Map<Section, List<StudyTask>> day = byDate.get(date);
            if (day == null || !day.containsKey(section) || day.get(section).isEmpty()) {
                return null;
            }
            return day.get(section).get(0);
        }

        private boolean hasSection(LocalDate date, Section section, StudyTask ignore) {
            Map<Section, List<StudyTask>> day = byDate.get(date);
            if (day == null || !day.containsKey(section)) {
                return false;
            }
            for (StudyTask t : day.get(section)) {
                if (t != ignore) {
                    return true;
                }
            }
            return false;
        }

        private void markUnresolvable(LocalDate date, Section section) {
            unresolvable.add(date + "#" + section);
        }

        private boolean isUnresolvable(LocalDate date, Section section) {
            return unresolvable.contains(date + "#" + section);
        }
    }
}
