package com.amilingo.platform.module.lang.dto.response;

import java.util.List;
import java.util.Map;

/**
 * 写作批改响应（统一 schema）。
 *
 * <p>字段与 Python 端 {@code writing_service.grade_essay} 返回结构对齐，
 * 支持 CET（8 维）和 IELTS（TR/CC/LR/GRA 4 维）等多种考试，
 * 前端只需根据 {@code examType} 决定维度渲染方式。
 */
public record WritingGradeResponse(
    String examType,           // 考试类型：CET4/CET6/IELTS_A/IELTS_G
    String topic,              // 作文题目
    String level,              // 考试级别
    Integer wordCount,         // 词数
    List<Map<String, Object>> basicIssues,  // 基础规则检查结果

    // ---- 总分 ----
    Double overall,            // 总分（CET: 0-15; IELTS: 0-9）
    String band,               // 档位
    String bandComment,        // 档位官方描述
    Double total106,           // CET 106.5 分制换算
    Double totalScorePercent,  // 百分比

    // ---- 诊断 ----
    Map<String, Object> dimensions,         // 各维度分数
    Map<String, String> dimensionLabels,    // 各维度中文标签
    Map<String, String> dimensionComments,  // 各维度简评
    String feedback,            // 总评
    List<String> nextActions,   // 下一步建议

    // ---- 逐句批改 ----
    List<Map<String, Object>> sentences,
    String summary,

    // ---- 范文 ----
    List<Map<String, Object>> similarEssays,

    String generatedAt
) {
}
