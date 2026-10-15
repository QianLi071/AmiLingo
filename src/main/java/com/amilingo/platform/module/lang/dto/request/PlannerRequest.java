package com.amilingo.platform.module.lang.dto.request;

import java.util.List;
import java.util.Map;

/**
 * 生成排程请求（对应前端测评 profile）。字段均可空，缺失时服务端回退到已保存的考试目标。
 */
public record PlannerRequest(
        String examType,
        String examDate,
        String region,
        Double weeklyHours,
        Integer vocabulary,
        Map<String, Double> current,
        Map<String, Double> target,
        Double targetTotal,
        List<String> weak
) {
}
