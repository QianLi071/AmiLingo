package com.amilingo.platform.module.lang.planner;

import com.amilingo.platform.module.lang.enums.Section;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

/**
 * 阶段模型：根据距考天数判定备考阶段，并提供各阶段分科时间分配基线权重。
 *
 * <ul>
 *   <li>{@link Stage#FOUNDATION}（摸底/基础）：距考 &gt; 60 天，打基础、补词汇、熟悉题型。</li>
 *   <li>{@link Stage#INTENSIVE}（强化）：距考 30–60 天，专项训练、精练提分。</li>
 *   <li>{@link Stage#SPRINT}（冲刺）：距考 ≤ 30 天，模考、查漏补缺、应试技巧。</li>
 * </ul>
 */
@Component
public class StageModel {

    public enum Stage {
        FOUNDATION("基础期"),
        INTENSIVE("强化期"),
        SPRINT("冲刺期");

        private final String displayName;

        Stage(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /** 判定某日期所处阶段。 */
    public Stage stageFor(LocalDate from, LocalDate examDate) {
        long days = ChronoUnit.DAYS.between(from, examDate);
        if (days > 60) {
            return Stage.FOUNDATION;
        }
        if (days > 30) {
            return Stage.INTENSIVE;
        }
        return Stage.SPRINT;
    }

    /**
     * 各阶段分科时间基线权重（在按差距加权前的先验，保证每科都有基本投入）。
     * 基础期偏重输入（听/读/词汇），冲刺期偏重输出与模考（写/说）。
     */
    public Map<Section, Double> baselineWeights(Stage stage) {
        Map<Section, Double> weights = new EnumMap<>(Section.class);
        switch (stage) {
            case FOUNDATION -> {
                weights.put(Section.LISTENING, 1.1);
                weights.put(Section.READING, 1.1);
                weights.put(Section.WRITING, 0.8);
                weights.put(Section.SPEAKING, 0.8);
            }
            case INTENSIVE -> {
                weights.put(Section.LISTENING, 1.0);
                weights.put(Section.READING, 1.0);
                weights.put(Section.WRITING, 1.0);
                weights.put(Section.SPEAKING, 1.0);
            }
            case SPRINT -> {
                weights.put(Section.LISTENING, 0.9);
                weights.put(Section.READING, 0.9);
                weights.put(Section.WRITING, 1.15);
                weights.put(Section.SPEAKING, 1.15);
            }
        }
        return weights;
    }
}
