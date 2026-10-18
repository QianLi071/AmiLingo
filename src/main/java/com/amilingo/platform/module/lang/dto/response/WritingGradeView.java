package com.amilingo.platform.module.lang.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 前端写作评分视图：字段名与前端 api.js 读取的 snake_case 对齐，
 * 内容与原版 Python {@code grade_essay} 返回一致（多轮：评分 + 逐句 + 范文检索）。
 */
public record WritingGradeView(
        @JsonProperty("total_score") double totalScore,
        Map<String, Double> scores,
        String band,
        @JsonProperty("band_comment") String bandComment,
        @JsonProperty("next_actions") List<String> nextActions,
        @JsonProperty("dimension_comments") Map<String, String> dimensionComments,
        /** 逐句批改：每项 {index, original, issues[], revised, comment}。 */
        List<Map<String, Object>> sentences,
        /** 图片评分时为模型识别出的作文原文；文本评分为 null。 */
        String content,
        @JsonProperty("word_count") Integer wordCount,
        @JsonProperty("basic_issues") List<Map<String, Object>> basicIssues,
        /** 评分轮总评。 */
        @JsonProperty("overall_comment") String overallComment,
        /** 逐句批改轮的整体性归纳（2-3 条改进方面）。 */
        String summary,
        @JsonProperty("similar_essays") List<Map<String, Object>> similarEssays
) {
}
