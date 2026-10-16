package com.amilingo.platform.module.lang.planner;

import com.amilingo.platform.module.lang.enums.ExamType;
import com.amilingo.platform.module.lang.enums.Section;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 长周期规划输入。
 *
 * @param userId        用户 ID
 * @param examType      考试类型
 * @param startDate     用户定义的周期开始日期（引擎会对齐到当周周一）
 * @param endDate       周期结束日期（通常为考试日）
 * @param currentScores 当前分科分（L/R/W/S）
 * @param targetScores  目标分科分（L/R/W/S）
 * @param targetTotal   目标总分
 * @param weakSections  薄弱分科（有序）
 */
public record CyclePlanInput(
        Long userId,
        ExamType examType,
        LocalDate startDate,
        LocalDate endDate,
        Map<String, Double> currentScores,
        Map<String, Double> targetScores,
        Double targetTotal,
        List<Section> weakSections
) {
}
