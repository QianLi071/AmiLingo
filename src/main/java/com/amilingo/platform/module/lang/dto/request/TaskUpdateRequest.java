package com.amilingo.platform.module.lang.dto.request;

/**
 * 用户手动修改 AI 生成的时间切片任务（局部修改）。字段为 null 表示不改。
 */
public record TaskUpdateRequest(
        String title,
        String objective,
        String start,
        String end
) {
}
