package com.amilingo.platform.module.lang.planner;

import com.amilingo.platform.common.util.Snowflake;
import com.amilingo.platform.module.lang.ai.PlanNarrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 排程引擎：生成覆盖整个备考周期的学习计划。严格按 Javadoc 固化的五步落地：
 * <ol>
 *   <li>计算任务量：{@link QuotaCalculator}（阶段模型 + 差距档位 + 薄弱加成）。</li>
 *   <li>抽取可用时间槽：{@link SlotExtractor}（课表扣减，过滤 &lt;30 分钟碎片）。</li>
 *   <li>贪心分配：{@link TaskAllocator}（薄弱优先，落入具体时间槽）。</li>
 *   <li>连续性校验：{@link ContinuityRule}（同分科不连续 3 天）。</li>
 *   <li>AI 文案：{@link PlanNarrator} 生成本周目标，失败则确定性兜底。</li>
 * </ol>
 */
@Slf4j
@Component
@Deprecated(since = "2026-09，由长周期规划引擎 CyclePlanner + CyclePlannerService 取代；旧入口经 PlannerService 适配层委托新引擎，本类仅保留空跑观察，勿新增调用")
public class PlanGenerator {

    /** 每周未满足任务量超过该阈值（分钟）时给出警告。 */
    private static final int UNMET_WARN_MINUTES = 60;

    private final StageModel stageModel;
    private final QuotaCalculator quotaCalculator;
    private final SlotExtractor slotExtractor;
    private final TaskAllocator taskAllocator;
    private final ContinuityRule continuityRule;
    private final PlanNarrator planNarrator;

    public PlanGenerator(StageModel stageModel,
                         QuotaCalculator quotaCalculator,
                         SlotExtractor slotExtractor,
                         TaskAllocator taskAllocator,
                         ContinuityRule continuityRule,
                         PlanNarrator planNarrator) {
        this.stageModel = stageModel;
        this.quotaCalculator = quotaCalculator;
        this.slotExtractor = slotExtractor;
        this.taskAllocator = taskAllocator;
        this.continuityRule = continuityRule;
        this.planNarrator = planNarrator;
    }

    public PlanResult generate(PlanInput input) {
        validate(input);

        Long planId = Snowflake.nextId();
        List<String> warnings = new ArrayList<>();
        List<StudyTask> allTasks = new ArrayList<>();

        LocalDate weekStart = input.planStart();
        boolean warnedCapacity = false;

        while (!weekStart.isAfter(input.examDate())) {
            LocalDate weekEnd = weekStart.plusDays(6);
            if (weekEnd.isAfter(input.examDate())) {
                weekEnd = input.examDate();
            }

            StageModel.Stage stage = stageModel.stageFor(weekStart, input.examDate());
            WeeklyQuota quota = quotaCalculator.calc(input, stage);
            List<TimeSlot> slots = slotExtractor.extract(input.userId(), weekStart, weekEnd);
            List<StudyTask> weekTasks = taskAllocator.allocate(
                    input.userId(), planId, stage, quota, slots, input.weakSections());

            long slotCapacity = slots.stream().mapToLong(TimeSlot::durationMinutes).sum();
            if (!warnedCapacity && slotCapacity < quota.totalMinutes()) {
                warnings.add(String.format(
                        "每周可用空闲时间（约 %d 分钟）少于达成目标所需（约 %d 分钟），建议增加每周学习时长或精简目标。",
                        slotCapacity, quota.totalMinutes()));
                warnedCapacity = true;
            }

            int allocatedMinutes = weekTasks.stream().mapToInt(StudyTask::getDurationMinutes).sum();
            int unmet = quota.totalMinutes() - allocatedMinutes;
            if (unmet > UNMET_WARN_MINUTES && weekTasks.size() < 3) {
                warnings.add("部分周的时间槽过碎，已尽量排程但任务量未排满，可在空闲时段手动补充练习。");
            }

            allTasks.addAll(weekTasks);
            weekStart = weekEnd.plusDays(1);
        }

        // Step 4 连续性规则
        continuityRule.adjust(allTasks);

        // Step 5 AI 文案（尽力，失败兜底）
        StageModel.Stage currentStage = stageModel.stageFor(input.planStart(), input.examDate());
        String weeklyGoal = safeNarrate(input, currentStage, allTasks);

        if (allTasks.isEmpty()) {
            warnings.add("未能生成任何任务，请检查课表占用与考试日期设置。");
        }

        return new PlanResult(planId, allTasks, weeklyGoal, warnings);
    }

    private String safeNarrate(PlanInput input, StageModel.Stage stage, List<StudyTask> tasks) {
        try {
            return planNarrator.narrate(input, stage, tasks);
        } catch (Exception e) {
            log.warn("PlanNarrator 生成文案失败，使用兜底文案: {}", e.getMessage());
            long taskCount = tasks.stream().filter(t -> !t.getScheduledStart().toLocalDate()
                    .isAfter(input.planStart().plusDays(6))).count();
            return String.format("本周处于%s，已安排约 %d 项学习任务，重点突破薄弱分科，坚持按计划完成并及时复盘。",
                    stage.getDisplayName(), taskCount);
        }
    }

    private static void validate(PlanInput input) {
        if (input == null || input.userId() == null) {
            throw new IllegalArgumentException("排程输入或用户 ID 不能为空");
        }
        if (input.examDate() == null || input.planStart() == null) {
            throw new IllegalArgumentException("考试日期与计划开始日期不能为空");
        }
        if (input.examDate().isBefore(input.planStart())) {
            throw new IllegalArgumentException("考试日期不能早于计划开始日期");
        }
        if (input.weeklyHours() <= 0) {
            throw new IllegalArgumentException("每周可投入学习时长必须大于 0");
        }
    }
}
