package com.amilingo.platform.module.lang.planner;

import java.util.List;

/**
 * 长周期规划结果：周期 + 月度单元 + 周单元 + 落到时间切片的任务 + 警告。
 */
public record CyclePlanResult(
        PlanCycle cycle,
        List<CyclePlanner.MonthPlan> months,
        List<CyclePlanner.WeekPlan> weeks,
        List<StudyTask> tasks,
        List<String> warnings
) {
}
