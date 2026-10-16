package com.amilingo.platform.module.lang.planner;

import com.amilingo.platform.module.lang.enums.Section;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 每周任务量配额：各分科每周应投入的分钟数。
 *
 * @param minutesBySection 分科 → 每周分钟数
 */
@Deprecated(since = "2026-09，旧版周配额结构，由 CyclePlanner 的月/周配额模型取代")
public record WeeklyQuota(Map<Section, Integer> minutesBySection) {

    public WeeklyQuota {
        minutesBySection = new LinkedHashMap<>(minutesBySection);
    }

    /** 每周总分钟数。 */
    public int totalMinutes() {
        return minutesBySection.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int minutesOf(Section section) {
        return minutesBySection.getOrDefault(section, 0);
    }
}
