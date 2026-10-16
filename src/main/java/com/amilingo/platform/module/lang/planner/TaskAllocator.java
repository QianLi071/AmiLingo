package com.amilingo.platform.module.lang.planner;

import com.amilingo.platform.module.lang.enums.Section;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务分配器（PlanGenerator Step 3）：贪心把各分科配额装进空闲时间槽。
 *
 * <p>规则：
 * <ul>
 *   <li>分科优先级：薄弱分科优先（最薄弱最先），其余按听→读→写→说。</li>
 *   <li>时间顺序：每个分科总是占用当前最早的可用槽段。</li>
 *   <li>任务粒度：按分科目标时长切分（写作一篇约 50–60 分钟等），最短不少于 30 分钟。</li>
 * </ul>
 */
@Component
@Deprecated(since = "2026-09，旧版贪心分配器，由 TimeSlicer + SliceAllocator 的难度切片分配取代，勿新增调用")
public class TaskAllocator {

    /** 单任务最短分钟数。 */
    private static final int MIN_TASK_MINUTES = 30;

    /** 时间取整粒度（分钟）。 */
    private static final int GRANULARITY = 15;

    /**
     * 分配一周任务。
     *
     * @param userId   用户 ID
     * @param planId   计划 ID
     * @param stage    本周阶段
     * @param quota    本周配额
     * @param slots    本周空闲槽
     * @param weak     薄弱分科（有序）
     * @return 已按时间排序的学习任务
     */
    public List<StudyTask> allocate(Long userId, Long planId, StageModel.Stage stage,
                                    WeeklyQuota quota, List<TimeSlot> slots, List<Section> weak) {
        // 剩余可装片段（按时间排序）
        List<Segment> segments = new ArrayList<>();
        for (TimeSlot slot : slots) {
            if (slot.durationMinutes() >= MIN_TASK_MINUTES) {
                segments.add(new Segment(slot.start(), slot.end()));
            }
        }

        List<StudyTask> tasks = new ArrayList<>();
        // 跨天均摊：限制单日负载，避免把整周任务都堆到最早的一天
        Map<LocalDate, Integer> dayLoad = new HashMap<>();
        int dailyCap = dailyCap(segments, quota.totalMinutes());

        for (Section section : orderSections(weak)) {
            int remaining = quota.minutesOf(section);
            int templateIndex = 0;
            while (remaining >= MIN_TASK_MINUTES) {
                int desired = desiredMinutes(section, stage);
                Segment target = findEarliestFitting(segments, Math.min(desired, remaining),
                        dayLoad, dailyCap);
                if (target == null) {
                    break; // 本周可用时间不足以容纳该科剩余任务量
                }
                int size = (int) Math.min(Math.min(desired, remaining), target.availableMinutes());
                if (size < MIN_TASK_MINUTES) {
                    break;
                }
                LocalDateTime start = target.cursor;
                LocalDateTime end = start.plusMinutes(size);
                TaskTemplate template = templateFor(section, stage, templateIndex++);
                tasks.add(StudyTask.builder()
                        .userId(userId)
                        .planId(planId)
                        .section(section)
                        .taskType(template.taskType())
                        .title(template.title())
                        .description(template.description())
                        .scheduledStart(start)
                        .scheduledEnd(end)
                        .durationMinutes(size)
                        .stage(stage.name())
                        .done(false)
                        .build());
                target.advance(size);
                dayLoad.merge(target.date, size, Integer::sum);
                remaining -= size;
            }
        }

        tasks.sort((a, b) -> a.getScheduledStart().compareTo(b.getScheduledStart()));
        return tasks;
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

    /** 分科单任务目标时长。 */
    private static int desiredMinutes(Section section, StageModel.Stage stage) {
        return switch (section) {
            case LISTENING -> stage == StageModel.Stage.SPRINT ? 60 : 50;
            case READING -> stage == StageModel.Stage.SPRINT ? 60 : 50;
            case WRITING -> 55;
            case SPEAKING -> 45;
        };
    }

    /**
     * 选择最早可容纳任务的段；优先选当日累计负载未超 {@code dailyCap} 的段以实现跨天均摊，
     * 若所有日期都已超上限，则回落到最早的可容纳段（保证仍尽量排满）。
     */
    private static Segment findEarliestFitting(List<Segment> segments, long minutes,
                                                Map<LocalDate, Integer> dayLoad, int dailyCap) {
        Segment overflow = null;
        for (Segment segment : segments) {
            if (segment.availableMinutes() < minutes) {
                continue;
            }
            if (overflow == null) {
                overflow = segment;
            }
            int load = dayLoad.getOrDefault(segment.date, 0);
            if (load + minutes <= dailyCap) {
                return segment;
            }
        }
        return overflow;
    }

    /**
     * 单日负载上限：按本周任务量在可安排天数上的均值上浮 30%，按 15 分钟取整，
     * 且不低于 90 分钟（保证单任务可放下），从而把任务摊到多个日期。
     */
    private static int dailyCap(List<Segment> segments, int totalMinutes) {
        long days = segments.stream().map(s -> s.date).distinct().count();
        if (days == 0) {
            return Integer.MAX_VALUE;
        }
        double avg = totalMinutes / (double) days;
        int cap = (int) Math.round(avg * 1.3 / GRANULARITY) * GRANULARITY;
        return Math.max(90, cap);
    }

    private static TaskTemplate templateFor(Section section, StageModel.Stage stage, int index) {
        return switch (section) {
            case LISTENING -> switch (index % 3) {
                case 0 -> new TaskTemplate("精听", "Section 精听训练：听写关键句、跟读纠音", "精听训练");
                case 1 -> new TaskTemplate("听力套题", "限时完成一套听力，对答案并复盘错题", "听力套题");
                default -> new TaskTemplate("泛听磨耳朵", "泛听真题音频/播客，捕捉主旨与细节", "泛听训练");
            };
            case READING -> switch (index % 3) {
                case 0 -> new TaskTemplate("精读", "精读真题文章：长难句拆解、同义替换整理", "精读训练");
                case 1 -> new TaskTemplate("阅读套题", "限时完成一套阅读，分析题型与定位", "阅读套题");
                default -> new TaskTemplate("词汇与长难句", "核心词汇复习 + 长难句结构分析", "词汇语法");
            };
            case WRITING -> switch (index % 2) {
                case 0 -> new TaskTemplate("写作成文", "限时完成一篇作文并用 AI 评分", "写作训练");
                default -> new TaskTemplate("写作素材", "范文学习、论点与连接词积累", "写作素材");
            };
            case SPEAKING -> switch (index % 2) {
                case 0 -> new TaskTemplate("口语 Part 训练", "按 Part1/2/3 限时作答并录音", "口语训练");
                default -> new TaskTemplate("口语素材", "话题卡准备、地道表达与发音练习", "口语素材");
            };
        };
    }

    /** 时间槽内尚未被占用的可写片段。 */
    private static final class Segment {
        private final LocalDate date;
        private LocalDateTime cursor;
        private final LocalDateTime end;

        private Segment(LocalDateTime start, LocalDateTime end) {
            this.date = start.toLocalDate();
            this.cursor = start;
            this.end = end;
        }

        private long availableMinutes() {
            return java.time.Duration.between(cursor, end).toMinutes();
        }

        private void advance(long minutes) {
            cursor = cursor.plusMinutes(minutes);
        }
    }

    private record TaskTemplate(String title, String description, String taskType) {
    }
}
