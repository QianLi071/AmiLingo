package com.amilingo.platform.infra.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AiGateway 的本地 llama.cpp 实现：直连两个 llama-server 实例（与原版 Python ai_client.py 同构）。
 *
 * <ul>
 * <li>对话 / 评分：{@code ai.python.llm-base-url}（默认 http://127.0.0.1:8081）
 * —— Gemma 4 E2B Q4_0 + mmproj，OpenAI 兼容 /v1/chat/completions，支持多模态；</li>
 * <li>向量 / 范文检索：{@code ai.python.embedding-base-url}（默认 http://127.0.0.1:8082）
 * —— Qwen3-Embedding-0.6B Q8_0，OpenAI 兼容 /v1/embeddings。</li>
 * </ul>
 *
 * <p>
 * 关键点：
 * </p>
 * <ul>
 * <li>结构化输出显式传 {@code response_format=json_object}（llama.cpp 支持），
 * 并对小模型偶发的 markdown 包裹/夹带说明做括号配对抽取兜底；</li>
 * <li>反序列化同时兼容 camelCase 与 snake_case（模型常把 dimensionComments 写成
 * dimension_comments）；</li>
 * <li>本地小模型推理慢，读取超时 600s；连接失败给出"先启动 llama-server"的明确提示。</li>
 * </ul>
 *
 * <p>
 * 仅在 {@code ai.provider=python} 时激活。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "python")
public class PythonAiGateway implements AiGateway {

    private final ObjectMapper objectMapper;
    /** snake_case 容错 mapper：小模型输出字段名不稳定（dimension_comments / dimensionComments）。 */
    private final ObjectMapper snakeMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${ai.python.llm-base-url:http://127.0.0.1:8081}")
    private String llmBaseUrl;
    @Value("${ai.python.embedding-base-url:http://127.0.0.1:8082}")
    private String embeddingBaseUrl;
    /** llama-server 不校验模型名，但 OpenAI 接口要求必传。 */
    @Value("${ai.python.chat-model:local-gemma}")
    private String chatModel;
    @Value("${ai.python.embed-model:local-qwen-embed}")
    private String embedModel;
    @Value("${ai.python.read-timeout:600000}")
    private long readTimeoutMs;

    public PythonAiGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.snakeMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
    }

    // ------------------------------------------------------------------
    // 对话（llama-server 8081）
    // ------------------------------------------------------------------

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt));
        return completeChat(messages, false, 0.7, 4096);
    }

    @Override
    public <T> T chatJson(String systemPrompt, String userPrompt, Class<T> responseType) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt));
        String raw = stripCodeFence(completeChat(messages, true, 0.3, 4096));
        try {
            return objectMapper.readValue(raw, responseType);
        } catch (Exception firstFail) {
            // 小模型字段命名不稳定：抽取首个 JSON 对象，并尝试 snake_case 映射
            JsonNode extracted = extractJsonObject(raw);
            if (extracted != null) {
                try {
                    return objectMapper.treeToValue(extracted, responseType);
                } catch (Exception ignored) {
                    // 继续尝试 snake
                }
                try {
                    return snakeMapper.treeToValue(extracted, responseType);
                } catch (Exception ignored) {
                    // 落到下面抛错
                }
            }
            try {
                return snakeMapper.readValue(raw, responseType);
            } catch (Exception e) {
                log.warn("本地模型 JSON 无法解析为 {}，原文片段: {}",
                        responseType.getSimpleName(), abbreviate(raw));
                throw new com.amilingo.platform.common.exceptions.ApiException(
                        HttpStatus.BAD_GATEWAY, "本地模型返回内容无法解析为 JSON: " + e.getMessage());
            }
        }
    }

    @Override
    public String chatJsonUser(String userPrompt, double temperature, int maxTokens) {
        // 与原版 Python 评分/逐句调用一致：只发一条 user 消息，JSON 模式，原文返回（去代码块）
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt));
        return stripCodeFence(completeChat(messages, true, temperature, maxTokens));
    }

    // ------------------------------------------------------------------
    // 向量（llama-server 8082）
    // ------------------------------------------------------------------

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", embedModel);
        body.put("input", texts);
        // 注意：embeddings 响应是 {data:[{embedding:[...]}]}，不能走 chat 的 choices 解析
        String resp = postJson(embeddingBaseUrl + "/v1/embeddings", body, "向量服务(8082)");
        try {
            JsonNode root = objectMapper.readTree(resp);
            JsonNode data = root.path("data");
            List<float[]> vectors = new ArrayList<>(texts.size());
            for (JsonNode item : data) {
                JsonNode emb = item.path("embedding");
                float[] vec = new float[emb.size()];
                for (int i = 0; i < emb.size(); i++) {
                    vec[i] = (float) emb.get(i).asDouble();
                }
                vectors.add(vec);
            }
            if (vectors.isEmpty()) {
                throw new IllegalStateException("响应 data 为空");
            }
            return vectors;
        } catch (Exception e) {
            throw new com.amilingo.platform.common.exceptions.ApiException(
                    HttpStatus.BAD_GATEWAY, "本地向量服务返回解析失败: " + e.getMessage());
        }
    }

    @Override
    public double similarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0, na = 0.0, nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0.0;
        }
        return Math.max(-1.0, Math.min(1.0, dot / (Math.sqrt(na) * Math.sqrt(nb))));
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------

    private String completeChat(List<Map<String, String>> messages, boolean jsonMode,
            double temperature, int maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", chatModel);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("stream", false);
        body.put("max_tokens", maxTokens);
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        // chat 响应才是 {choices:[{message:{content}}]}；embeddings 走 embed() 自行解析
        String resp = postJson(llmBaseUrl + "/v1/chat/completions", body, "对话模型(8081)");
        try {
            JsonNode root = objectMapper.readTree(resp);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new com.amilingo.platform.common.exceptions.ApiException(
                        HttpStatus.BAD_GATEWAY, "本地模型返回内容为空");
            }
            return content.asText();
        } catch (com.amilingo.platform.common.exceptions.ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new com.amilingo.platform.common.exceptions.ApiException(
                    HttpStatus.BAD_GATEWAY, "本地模型响应解析失败: " + e.getMessage());
        }
    }

    /** 发送 JSON 请求并做状态码校验，成功时原样返回响应体（chat / embeddings 各自解析）。 */
    private String postJson(String url, Map<String, Object> body, String serviceLabel) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new com.amilingo.platform.common.exceptions.ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "构造本地模型请求失败: " + e.getMessage());
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(readTimeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.error("无法连接本地 {}: {}", serviceLabel, e.getMessage());
            throw new com.amilingo.platform.common.exceptions.ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "无法连接本地" + serviceLabel + "（" + url + "）。请先启动 llama-server（start_llama.bat / start_embed.bat）。");
        }
        if (response.statusCode() / 100 != 2) {
            log.warn("本地 {} 返回非 2xx: {} {}", serviceLabel, response.statusCode(),
                    abbreviate(response.body()));
            throw new com.amilingo.platform.common.exceptions.ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "本地" + serviceLabel + "返回 " + response.statusCode() + ": " + abbreviate(response.body()));
        }
        return response.body();
    }

    // ------------------------------------------------------------------
    // JSON 抽取（与 DeepSeekAiGateway 同款，小模型输出更不稳定，必须保留）
    // ------------------------------------------------------------------

    private static String stripCodeFence(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline >= 0) {
                t = t.substring(firstNewline + 1);
            }
            int lastFence = t.lastIndexOf("```");
            if (lastFence >= 0) {
                t = t.substring(0, lastFence);
            }
        }
        return t.trim();
    }

    /** 从夹带说明文字的输出中抽取第一个平衡的 JSON 对象（简单括号配对，忽略字符串内括号）。 */
    private JsonNode extractJsonObject(String text) {
        try {
            int start = text.indexOf('{');
            if (start < 0) {
                return null;
            }
            int depth = 0;
            boolean inStr = false;
            boolean escape = false;
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (inStr) {
                    if (escape) {
                        escape = false;
                    } else if (c == '\\') {
                        escape = true;
                    } else if (c == '"') {
                        inStr = false;
                    }
                } else if (c == '"') {
                    inStr = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return objectMapper.readTree(text.substring(start, i + 1));
                    }
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
