package com.amilingo.platform.module.lang.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 前端图片写作评分请求。 */
public record WritingImageApiRequest(
        @JsonProperty("image_base64") String imageBase64,
        String topic,
        String level,
        @JsonProperty("exam_type") String examType,
        @JsonProperty("image_type") String imageType
) {
}
