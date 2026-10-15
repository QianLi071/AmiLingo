package com.amilingo.platform.module.lang.dto.response;

import java.util.Map;

/** 考试目标视图（目标 + 单科目标分）。 */
public record GoalView(
        Long goalId,
        String examType,
        String examDate,
        String region,
        Double targetTotal,
        Map<String, Double> targetParts,
        String createdAt
) {
}
