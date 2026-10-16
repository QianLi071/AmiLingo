package com.amilingo.platform.module.lang.dto.request;

import java.util.List;
import java.util.Map;

/**
 * 长周期规划生成请求。字段可空，缺失时回退到已保存的考试目标与默认值。
 *
 * @param startDate 周期开始日期 yyyy-MM-dd（可空，默认今天，引擎自动对齐周一）
 * @param endDate   周期结束/考试日期 yyyy-MM-dd
 */
public record CyclePlanRequest(
        String examType,
        String startDate,
        String endDate,
        Map<String, Double> current,
        Map<String, Double> target,
        Double targetTotal,
        List<String> weak
) {
}
