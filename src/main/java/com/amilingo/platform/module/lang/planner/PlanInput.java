package com.amilingo.platform.module.lang.planner;

import com.amilingo.platform.module.lang.enums.ExamType;
import com.amilingo.platform.module.lang.enums.Section;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 排程输入。
 *
 * @param userId         用户 ID
 * @param examType       考试类型
 * @param examDate       考试日期
 * @param planStart      计划开始日期（通常为今天）
 * @param currentScores  当前分科分（键 L/R/W/S）
 * @param targetScores   目标分科分（键 L/R/W/S）
 * @param targetTotal    目标总分
 * @param weakSections   薄弱分科（按薄弱程度排序）
 * @param weeklyHours    每周可投入学习小时数
 * @param vocabulary     当前词汇量
 */
public record PlanInput(
        Long userId,
        ExamType examType,
        LocalDate examDate,
        LocalDate planStart,
        Map<String, Double> currentScores,
        Map<String, Double> targetScores,
        Double targetTotal,
        List<Section> weakSections,
        double weeklyHours,
        int vocabulary
) {
}
