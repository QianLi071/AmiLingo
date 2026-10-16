package com.amilingo.platform.module.lang.planner;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 可用学习容量测算：基于时间资源模块的实际标记，统计日期区间内各类型可用分钟数。
 * 数据全部来自 {@link SlotExtractor#extractTyped}，不使用用户手填的「每周小时数」估算。
 */
@Component
public class CapacityCalculator {

    private final SlotExtractor slotExtractor;

    public CapacityCalculator(SlotExtractor slotExtractor) {
        this.slotExtractor = slotExtractor;
    }

    /** 区间容量（含端点）。 */
    public Capacity measure(Long userId, LocalDate from, LocalDate to) {
        List<TypedTimeSlot> slots = slotExtractor.extractTyped(userId, from, to);
        long deep = 0, free = 0, fragment = 0;
        for (TypedTimeSlot slot : slots) {
            long minutes = slot.durationMinutes();
            switch (slot.kind()) {
                case DEEP -> deep += minutes;
                case FRAGMENT -> fragment += minutes;
                case FREE -> free += minutes;
            }
        }
        return new Capacity(deep, free, fragment);
    }

    /**
     * @param deep     🟢 高效时段分钟
     * @param free     ⚪ 普通空闲分钟
     * @param fragment 🟡 碎片时段分钟
     */
    public record Capacity(long deep, long free, long fragment) {

        /** 全部可排程分钟（碎片也可承载轻量任务）。 */
        public long available() {
            return deep + free + fragment;
        }
    }
}
