package com.amilingo.platform.module.lang.dto.request;

import java.util.List;

/** 批量设置时间资源块请求（整体覆盖）。 */
public record ScheduleRequest(List<BlockInput> blocks) {

    public record BlockInput(
            Integer dayOfWeek,
            String startTime,
            String endTime,
            String blockType,
            String title,
            String recurrence,    // WEEKLY / ONCE
            String effectiveFrom, // YYYY-MM-DD
            String effectiveTo,   // YYYY-MM-DD
            String specificDate   // YYYY-MM-DD，recurrence=ONCE 时必填
    ) {
    }
}
