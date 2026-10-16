package com.amilingo.platform.module.lang.planner;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 可用时间槽：一段连续空闲时间。
 *
 * @param start 开始时间
 * @param end   结束时间
 */
public record TimeSlot(LocalDateTime start, LocalDateTime end) {

    /** 时长（分钟）。 */
    public long durationMinutes() {
        return Duration.between(start, end).toMinutes();
    }

    /** 是否足以容纳至少 minMinutes 分钟的任务。 */
    public boolean fits(long minMinutes) {
        return durationMinutes() >= minMinutes;
    }
}
