package com.amilingo.platform.module.lang.dto.request;

import java.util.Map;

/** 设置/更新考试目标请求。 */
public record GoalRequest(
        String examType,
        String examDate,
        String region,
        Double targetTotal,
        Map<String, Double> targetParts
) {
}
