package com.amilingo.platform.module.lang.planner;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 时间切片器：把一整段类型化空闲时间切分为多个「可绑定学习目标」的工作片段，
 * 片段之间自动插入休息（例：2h 高效空闲 → 50min + 10min 休息 + 50min）。
 *
 * <ul>
 *   <li>🟢 DEEP：50 分钟工作 / 10 分钟休息，尾部 ≥25 分钟才保留。</li>
 *   <li>⚪ FREE：45 分钟工作 / 10 分钟休息，尾部 ≥20 分钟才保留。</li>
 *   <li>🟡 FRAGMENT：≤25 分钟的短片段（25/15/10），不插休息。</li>
 * </ul>
 */
@Component
public class TimeSlicer {

    private static final int DEEP_WORK = 50;
    private static final int FREE_WORK = 45;
    private static final int BREAK_MINUTES = 10;
    private static final int DEEP_TAIL_KEEP = 25;
    private static final int FREE_TAIL_KEEP = 20;
    private static final int FRAGMENT_MAX = 25;
    private static final int FRAGMENT_MIN = 10;

    public List<WorkSlice> slice(List<TypedTimeSlot> slots) {
        List<WorkSlice> result = new ArrayList<>();
        for (TypedTimeSlot slot : slots) {
            switch (slot.kind()) {
                case DEEP -> result.addAll(sliceWithBreak(slot, DEEP_WORK, DEEP_TAIL_KEEP));
                case FREE -> result.addAll(sliceWithBreak(slot, FREE_WORK, FREE_TAIL_KEEP));
                case FRAGMENT -> result.addAll(sliceFragment(slot));
            }
        }
        return result;
    }

    /** 长时段按 workMin 切分，片段间扣 10 分钟休息。 */
    private static List<WorkSlice> sliceWithBreak(TypedTimeSlot slot, int workMin, int tailKeep) {
        List<WorkSlice> slices = new ArrayList<>();
        LocalDateTime cursor = slot.start();
        LocalDateTime end = slot.end();
        while (!cursor.isAfter(end)) {
            long remain = java.time.Duration.between(cursor, end).toMinutes();
            if (remain < Math.min(workMin, tailKeep)) {
                break;
            }
            int size = remain >= workMin ? workMin : (int) remain;
            LocalDateTime sliceEnd = cursor.plusMinutes(size);
            slices.add(new WorkSlice(cursor, sliceEnd, slot.kind()));
            cursor = sliceEnd.plusMinutes(BREAK_MINUTES); // 休息段不产出切片，保持空闲
        }
        // 末段不足 workMin 但 ≥ tailKeep 时已在循环内产出（remain 分支），此处无需再补
        return slices;
    }

    /** 碎片时段切短片段：优先 25，剩余 ≥10 时按 15/10 保留。 */
    private static List<WorkSlice> sliceFragment(TypedTimeSlot slot) {
        List<WorkSlice> slices = new ArrayList<>();
        LocalDateTime cursor = slot.start();
        LocalDateTime end = slot.end();
        while (true) {
            long remain = java.time.Duration.between(cursor, end).toMinutes();
            if (remain < FRAGMENT_MIN) break;
            int size = (int) Math.min(FRAGMENT_MAX, remain);
            slices.add(new WorkSlice(cursor, cursor.plusMinutes(size), TypedTimeSlot.Kind.FRAGMENT));
            cursor = cursor.plusMinutes(size);
        }
        return slices;
    }

    /** 工作时间切片（已扣除休息）。 */
    public record WorkSlice(LocalDateTime start, LocalDateTime end, TypedTimeSlot.Kind kind) {
        public int minutes() {
            return (int) java.time.Duration.between(start, end).toMinutes();
        }
    }
}
