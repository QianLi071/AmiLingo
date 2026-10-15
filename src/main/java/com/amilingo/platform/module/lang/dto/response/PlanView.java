package com.amilingo.platform.module.lang.dto.response;

import java.util.List;

/** 排程结果视图。 */
public record PlanView(
        Long planId,
        String weeklyGoal,
        List<String> warnings,
        int taskCount,
        List<TaskView> tasks
) {

    public record TaskView(
            Long taskId,
            String section,
            String title,
            String description,
            String start,
            String end,
            Integer durationMinutes,
            String stage,
            String taskType,
            boolean done
    ) {
    }
}
