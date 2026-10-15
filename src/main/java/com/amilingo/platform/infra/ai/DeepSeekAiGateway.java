package com.amilingo.platform.infra.ai;

import com.amilingo.platform.common.exceptions.ApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
 * AiGateway 的 DeepSeek 云端实现：直接通过 HTTP 调用 DeepSeek {@code /chat/completions}，
 * 不依赖本地 OfflineAssistant / llama.cpp / GGUF 模型。
 *
 * <p>当 models / llama.cpp 留空、Python 服务不启动时，此实现让写作/口语评分、薄弱点分析、
 * 排程文案等全部 AI 能力真实可用。通过 {@code ai.provider=deepseek}（默认）激活。
 *
 * <p>{@link #embed(List)}：DeepSeek 不提供向量接口，而本地向量模型（8082）又未运行，
 * 故此处使用确定性的特征哈希向量器（离线、无依赖），仅用于范文检索排序与相似度，
 * 属可工作的降级实现；接入本地 Qwen 向量服务后可切换 provider=python。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "deepseek", matchIfMissing = true)
public class DeepSeekAiGateway implements AiGateway {

    private final ObjectMapper objectMapper;

    @Value("${ai.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;
    @Value("${ai.deepseek.api-key:}")
    private String apiKey;
    @Value("${ai.deepseek.model:deepseek-chat}")
    private String model;
    @Value("${ai.deepseek.connect-timeout:5000}")
    private long connectTimeout;
    @Value("${ai.deepseek.read-timeout:360000}")
    private long readTimeout;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 特征哈希向量维度。 */
    private static final int EMBED_DIM = 512;

    public DeepSeekAiGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));
        return complete(messages, null, 0.4);
    }

    @Override
    public String chatJsonUser(String userPrompt, double temperature, int maxTokens) {
        // 与本地版同构：只发一条 user 消息，prompt 末尾追加 JSON 契约
        String prompt = (userPrompt == null ? "" : userPrompt)
                + "\n你必须只输出一个合法 JSON 对象，不要输出任何解释、前后缀文字或 Markdown 代码块（禁止 ```json）。";
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", prompt));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("response_format", Map.of("type", "json_object"));
        return stripCodeFence(doPost(body));
    }

    @Override
    public <T> T chatJson(String systemPrompt, String userPrompt, Class<T> responseType) {
        String jsonSystem = systemPrompt
                + "\n你必须只输出一个合法 JSON 对象，不要输出任何解释、前后缀文字或 Markdown 代码块（禁止 ```json）。";
        String raw = stripCodeFence(chat(jsonSystem, userPrompt));
        try {
            return objectMapper.readValue(raw, responseType);
        } catch (Exception e) {
            log.warn("DeepSeek 返回内容无法直接反序列化为 {}，尝试抽取首个 JSON 对象。原文片段: {}",
                    responseType.getSimpleName(), abbreviate(raw));
            JsonNode extracted = extractJsonObject(raw);
            if (extracted != null) {
                try {
                    return objectMapper.treeToValue(extracted, responseType);
                } catch (Exception ignored) {
                    // 落到下面抛错
                }
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI 返回的 JSON 无法解析: " + e.getMessage());
        }
    }
    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(hashEmbed(text));
        }
        return result;
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
        double cos = dot / (Math.sqrt(na) * Math.sqrt(nb));
        return Math.max(-1.0, Math.min(1.0, cos));
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------
    private String complete(List<Map<String, String>> messages, String responseFormat, double temperature) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "未配置 DeepSeek API Key（ai.deepseek.api-key / DEEPSEEK_API_KEY）");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("temperature", temperature);
        if (responseFormat != null) {
            body.put("response_format", Map.of("type", responseFormat));
        }
        return doPost(body);
    }

    /** 发送 /chat/completions 请求并提取 choices[0].message.content。 */
    private String doPost(Map<String, Object> body) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "未配置 DeepSeek API Key（ai.deepseek.api-key / DEEPSEEK_API_KEY）");
        }

        HttpRequest request;
        try {
            String payload = objectMapper.writeValueAsString(body);
            request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(Duration.ofMillis(readTimeout))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "构造 AI 请求失败: " + e.getMessage());
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.error("调用 DeepSeek 失败: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务不可用: " + e.getMessage());
        }

        if (response.statusCode() / 100 != 2) {
            log.warn("DeepSeek 返回非 2xx: {} {}", response.statusCode(), abbreviate(response.body()));
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 服务返回 " + response.statusCode() + ": " + abbreviate(response.body()));
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI 返回内容为空");
            }
            return content.asText();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "解析 AI 响应失败: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 文本处理 / 特征哈希
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

    /** 从可能夹带解释文字的输出中抽取第一个平衡的 JSON 对象（简单括号配对，忽略字符串内括号）。 */
    private static JsonNode extractJsonObject(String text) {
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
                        return new ObjectMapper().readTree(text.substring(start, i + 1));
                    }
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    /** 确定性特征哈希向量：分词 → 稳定哈希到固定维度 → L2 归一化。离线、无外部依赖。 */
    private static float[] hashEmbed(String text) {
        float[] vec = new float[EMBED_DIM];
        if (text == null || text.isBlank()) {
            return vec;
        }
        String lower = text.toLowerCase();
        StringBuilder token = new StringBuilder();
        int count = 0;
        for (int i = 0; i <= lower.length(); i++) {
            char c = i < lower.length() ? lower.charAt(i) : ' ';
            boolean part = Character.isLetterOrDigit(c);
            if (part) {
                token.append(c);
            } else if (token.length() > 0) {
                String tok = token.toString();
                token.setLength(0);
                // unigram
                accumulate(vec, tok, 1.0f);
                count++;
            }
        }
        // 简单 bigram，捕捉一点词序
        String[] words = lower.split("[^a-z0-9]+");
        for (int i = 0; i + 1 < words.length; i++) {
            if (!words[i].isEmpty() && !words[i + 1].isEmpty()) {
                accumulate(vec, words[i] + "_" + words[i + 1], 0.5f);
            }
        }
        // L2 归一化
        double norm = 0.0;
        for (float v : vec) {
            norm += v * v;
        }
        if (norm > 0) {
            float n = (float) Math.sqrt(norm);
            for (int i = 0; i < vec.length; i++) {
                vec[i] /= n;
            }
        }
        return vec;
    }

    private static void accumulate(float[] vec, String token, float weight) {
        int h = stableHash(token);
        int idx = Math.abs(h % vec.length);
        float sign = ((h & 0x80000000) == 0) ? 1f : -1f;
        vec[idx] += sign * weight;
    }

    /** 32 位 FNV-1a，跨 JVM 稳定。 */
    private static int stableHash(String s) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= 0x01000193;
        }
        return hash;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
