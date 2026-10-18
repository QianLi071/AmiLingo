package com.amilingo.platform.module.lang.dto.request;

/** 选择题（听力/阅读）提交请求。 */
public record QuizApiRequest(
        Long questionId,
        String answer,
        String section,
        String examType,
        Integer durationSeconds
) {
}
