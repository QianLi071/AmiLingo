package com.amilingo.platform.module.lang.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 前端文本写作评分请求（字段对齐前端 api.js）。 */
public record WritingGradeApiRequest(
        String content,
        String topic,
        String level,
        @JsonProperty("exam_type") String examType
) {
}
