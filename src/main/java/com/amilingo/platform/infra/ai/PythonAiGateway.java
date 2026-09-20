package com.amilingo.platform.infra.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * AiGateway 的 Python 实现：通过 HTTP 调用 OfflineAssistant（FastAPI）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@code systemPrompt} 参数作为路由键：值为 {@code "writing_grade"} 时走写作批改接口
 *       {@code POST /api/v1/writing/grade}，{@code userPrompt} 为请求体 JSON 字符串。</li>
 *   <li>Prompt 内容由 Python 端根据 {@code exam_type} 从 registry 选择，Java 端不持有 Prompt。</li>
 *   <li>其余方法（chat/embed/similarity）暂未接入，抛出 {@link UnsupportedOperationException}。</li>
 * </ul>
 *
 * <p>{@code @Primary} 使其优先于 {@link NoopAiGateway} 注入。
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class PythonAiGateway implements AiGateway {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.python.base-url:http://localhost:8000}")
    private String pythonBaseUrl;

    /** 写作批改路由键：WritingScorer 调用 chatJson 时传入此 systemPrompt。 */
    public static final String ROUTE_WRITING_GRADE = "writing_grade";

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        throw new UnsupportedOperationException("PythonAiGateway#chat 尚未实现，待迭代3接入通用对话端点");
    }

    @Override
    public <T> T chatJson(String systemPrompt, String userPrompt, Class<T> responseType) {
        try {
            if (ROUTE_WRITING_GRADE.equals(systemPrompt)) {
                return gradeWriting(userPrompt, responseType);
            }
            throw new UnsupportedOperationException("未支持的 systemPrompt 路由: " + systemPrompt);
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            log.error("PythonAiGateway.chatJson 调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("调用 Python AI 服务失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        throw new UnsupportedOperationException("PythonAiGateway#embed 尚未实现，待迭代3接入向量端点");
    }

    @Override
    public double similarity(float[] a, float[] b) {
        throw new UnsupportedOperationException("PythonAiGateway#similarity 尚未实现，待迭代3接入相似度端点");
    }

    // ------------------------------------------------------------------
    // 写作批改：POST /api/v1/writing/grade
    // ------------------------------------------------------------------
    private <T> T gradeWriting(String userPromptJson, Class<T> responseType) throws Exception {
        // userPromptJson 是 WritingScorer 传入的请求体 JSON（exam_type/topic/content/level）
        Map<String, Object> requestBody = objectMapper.readValue(
                userPromptJson, new TypeReference<Map<String, Object>>() {});

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String url = pythonBaseUrl + "/api/v1/writing/grade";
        log.debug("调用 Python 写作批改: {}", url);

        String responseJson = restTemplate.postForObject(url, entity, String.class);
        return objectMapper.readValue(responseJson, responseType);
    }
}
