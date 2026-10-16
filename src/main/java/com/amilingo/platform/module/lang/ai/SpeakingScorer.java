package com.amilingo.platform.module.lang.ai;

import com.amilingo.platform.infra.ai.AiGateway;
import com.amilingo.platform.module.lang.ai.prompt.SpeakingPrompt;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 口语评分器：根据口语作答转写，经 {@link AiGateway} 输出 FC/LR/GRA/P 评分。
 */
@Component
public class SpeakingScorer {

    private final AiGateway aiGateway;

    public SpeakingScorer(AiGateway aiGateway) {
        this.aiGateway = aiGateway;
    }

    public SpeakingGradeResult score(String transcript, String topic) {
        String text = transcript != null ? transcript : "";
        String top = topic != null ? topic : "日常话题";
        String userPrompt = SpeakingPrompt.USER_TEMPLATE.formatted(top, text);
        AiSpeakingResult ai = aiGateway.chatJson(SpeakingPrompt.SYSTEM, userPrompt, AiSpeakingResult.class);
        if (ai == null) {
            return new SpeakingGradeResult(0.0, Map.of(), Map.of(), "评分失败", List.of());
        }

        Map<String, Double> scores = new LinkedHashMap<>();
        for (String key : List.of("FC", "LR", "GRA", "P")) {
            Double value = ai.scores() != null ? ai.scores().get(key) : null;
            scores.put(key, normalizeBand(value != null ? value : 0.0));
        }
        double overall = normalizeBand(ai.overall() != null ? ai.overall() : average(scores));

        return new SpeakingGradeResult(
                overall,
                scores,
                ai.dimensionComments() != null ? ai.dimensionComments() : Map.of(),
                ai.feedback() != null ? ai.feedback() : "",
                ai.nextActions() != null ? ai.nextActions() : List.of());
    }

    private static double average(Map<String, Double> scores) {
        return scores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static double normalizeBand(double value) {
        double v = Math.max(0, Math.min(9, value));
        return Math.round(v * 2) / 2.0;
    }

    /** 口语评分结果。 */
    public record SpeakingGradeResult(
            double overall,
            Map<String, Double> scores,
            Map<String, String> dimensionComments,
            String feedback,
            List<String> nextActions
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiSpeakingResult(
            Double overall,
            Map<String, Double> scores,
            Map<String, String> dimensionComments,
            String feedback,
            List<String> nextActions
    ) {
    }
}
