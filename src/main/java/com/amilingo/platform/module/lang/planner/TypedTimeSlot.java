package com.amilingo.platform.module.lang.planner;

import java.time.LocalDateTime;

/**
 * 类型化可用时间槽（24 小时制）：一段连续可用时间及其标签类型。
 * AI 依据类型匹配任务难度：DEEP→高难度，FREE→中等，FRAGMENT→轻量短任务。
 *
 * @param start 开始时间
 * @param end   结束时间
 * @param kind  槽类型
 */
public record TypedTimeSlot(LocalDateTime start, LocalDateTime end, Kind kind) {

    public enum Kind {
        DEEP,     // 🟢 高效深度学习
        FREE,     // ⚪ 普通空闲
        FRAGMENT  // 🟡 碎片琐事（仅轻量任务）
    }

    public long durationMinutes() {
        return java.time.Duration.between(start, end).toMinutes();
    }
}
