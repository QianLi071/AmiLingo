package com.amilingo.platform.module.lang.planner;

import com.amilingo.platform.module.lang.enums.Section;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 配额计算器（PlanGenerator Step 1）：按阶段基线 + 目标差距 + 薄弱加成，
 * 在用户每周可投入时长内，计算各分科每周应投入分钟数。
 */
@Component
@Deprecated(since = "2026-09，旧版周配额算法，由 CyclePlanner 的差距换算 + 月/周配额分配取代，勿新增调用")
public class QuotaCalculator {

    /** 每科每周最低分钟数（只要存在差距）。 */
    private static final int MIN_SECTION_MINUTES = 30;
    /** 分配结果按 15 分钟取整，便于排课。 */
    private static final int GRANULARITY = 15;

    private final StageModel stageModel;

    public QuotaCalculator(StageModel stageModel) {
        this.stageModel = stageModel;
    }

    public WeeklyQuota calc(PlanInput input, StageModel.Stage stage) {
        int totalMinutes = (int) Math.round(input.weeklyHours() * 60.0);

        Map<Section, Double> rawWeights = new EnumMap<>(Section.class);
        Map<Section, Double> baseline = stageModel.baselineWeights(stage);

        for (Section section : Section.values()) {
            double gap = gapOf(input, section);
            // 无差距的科目只保留很少的维持性投入
            double gapFactor = gap <= 0 ? 0.15 : (0.6 + Math.min(gap, 6.0));
            double weakBoost = weakBoost(input, section);
            rawWeights.put(section, baseline.getOrDefault(section, 1.0) * gapFactor * weakBoost);
        }

        double sumWeights = rawWeights.values().stream().mapToDouble(Double::doubleValue).sum();

        Map<Section, Integer> minutes = new EnumMap<>(Section.class);
        int allocated = 0;
        Section[] sections = Section.values();
        for (int i = 0; i < sections.length; i++) {
            Section section = sections[i];
            int m;
            if (i == sections.length - 1) {
                m = totalMinutes - allocated; // 最后一科兜底，吸收取整误差
            } else {
                double share = rawWeights.get(section) / sumWeights * totalMinutes;
                m = (int) Math.round(share / GRANULARITY) * GRANULARITY;
            }
            if (gapOf(input, section) > 0) {
                m = Math.max(m, MIN_SECTION_MINUTES);
            }
            m = Math.max(0, Math.min(m, totalMinutes));
            minutes.put(section, m);
            allocated += m;
        }

        return new WeeklyQuota(minutes);
    }

    private static double gapOf(PlanInput input, Section section) {
        String code = section.getCode();
        double current = input.currentScores() != null && input.currentScores().get(code) != null
                ? input.currentScores().get(code) : 0.0;
        double target = input.targetScores() != null && input.targetScores().get(code) != null
                ? input.targetScores().get(code) : 0.0;
        return Math.max(0.0, target - current);
    }

    private static double weakBoost(PlanInput input, Section section) {
        if (input.weakSections() == null) {
            return 1.0;
        }
        int idx = input.weakSections().indexOf(section);
        if (idx < 0) {
            return 1.0;
        }
        // 最薄弱 +30%，其余薄弱 +15%
        return idx == 0 ? 1.3 : 1.15;
    }
}
