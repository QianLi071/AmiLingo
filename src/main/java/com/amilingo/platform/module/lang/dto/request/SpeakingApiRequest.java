package com.amilingo.platform.module.lang.dto.request;

/** 口语评分请求（转写 + 话题）。 */
public record SpeakingApiRequest(
        String transcript,
        String topic
) {
}
