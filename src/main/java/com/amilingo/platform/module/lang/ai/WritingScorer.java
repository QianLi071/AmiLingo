package com.amilingo.platform.module.lang.ai;

import com.amilingo.platform.infra.ai.AiGateway;
import com.amilingo.platform.infra.ai.PythonAiGateway;
import com.amilingo.platform.module.lang.dto.request.WritingGradeRequest;
import com.amilingo.platform.module.lang.dto.response.WritingGradeResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WritingScorer {

    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    /**
     * 评分作文。
     *
     * <p>Prompt 由 Python 端根据 {@code examType} 从 registry 选择，Java 端只传参数，不持有 Prompt 文本。
     */
    public WritingGradeResponse score(WritingGradeRequest req) {
        Map<String, Object> payload = Map.of(
                "exam_type", req.examType() != null ? req.examType() : "CET4",
                "topic", req.prompt() != null ? req.prompt() : "",
                "content", req.essay() != null ? req.essay() : "",
                "level", mapLevel(req.examType())
        );

        String userPromptJson;
        try {
            userPromptJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化评分请求失败", e);
        }

        return aiGateway.chatJson(PythonAiGateway.ROUTE_WRITING_GRADE, userPromptJson, WritingGradeResponse.class);
    }

    /** exam_type → level（用于 Python 端词数规则和 Prompt 占位符）。 */
    private String mapLevel(String examType) {
        if (examType == null) return "四级";
        return switch (examType) {
            case "CET4" -> "四级";
            case "CET6" -> "六级";
            case "IELTS_A" -> "Academic";
            case "IELTS_G" -> "General";
            default -> "四级";
        };
    }
}
