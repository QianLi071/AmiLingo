package com.amilingo.platform.module.lang.planner;

import java.util.List;

/**
 * 排程结果。
 *
 * @param planId      本次生成计划的 ID
 * @param tasks       生成的学习任务（已按时间排序）
 * @param weeklyGoal  AI 生成的本周目标文案
 * @param warnings    生成过程中的警告（如时间不足以覆盖目标任务量）
 */
@Deprecated(since = "2026-09，旧版 PlanGenerator 的输出结构，由 CyclePlanResult 取代")
public record PlanResult(
        Long planId,
        List<StudyTask> tasks,
        String weeklyGoal,
        List<String> warnings
) {
}
