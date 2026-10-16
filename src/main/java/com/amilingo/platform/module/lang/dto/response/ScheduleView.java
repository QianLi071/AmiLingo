package com.amilingo.platform.module.lang.dto.response;

import java.util.List;

/** 每周时间资源视图（含每日占用/空闲统计）。 */
public record ScheduleView(
        List<DayView> days,
        int totalOccupiedMinutes,
        int totalFreeMinutes
) {

    public record DayView(
            Integer dayOfWeek,
            List<BlockView> blocks,
            int occupiedMinutes,
            int freeMinutes
    ) {
    }

    public record BlockView(
            Long blockId,
            String startTime,
            String endTime,
            String blockType,
            String title,
            String recurrence,
            String effectiveFrom,
            String effectiveTo,
            String specificDate
    ) {
    }
}
