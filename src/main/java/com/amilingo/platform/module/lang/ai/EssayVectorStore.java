package com.amilingo.platform.module.lang.ai;

import com.amilingo.platform.infra.ai.AiGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 范文向量检索（与原版 Python {@code embedding_service.EmbeddingService} 同构）。
 *
 * <ul>
 *   <li>范文库：{@code src/main/python/OfflineAssistant/backend/data/essays/*.json}，
 *       每个 JSON 为数组，元素含 id/title/topic/level/score/content；</li>
 *   <li>向量缓存：{@code .../backend/data/vector_store/fanwen_<内容md5指纹12位>.json}，
 *       指纹算法与原版一致（全部 content 以 "|" 拼接后取 MD5 前 12 位），
 *             因此可直接复用 Python 端已生成的缓存，范文未变时不会重复调用 8082；</li>
 *   <li>检索：当前作文经 Qwen 8082 向量化后，与同 level 范文算余弦相似度，取 top-k。</li>
 * </ul>
 *
 * <p>懒加载：首次调用才读范文/缓存，避免应用启动强依赖 8082；任何异常都降级为空列表，
 * 不阻塞评分主流程（与原版一致）。
 */
@Slf4j
@Component
public class EssayVectorStore {

    /** exam_type → 范文 level（同原版 EXAM_TO_LEVEL）。 */
    private static final Map<String, String> EXAM_TO_LEVEL = Map.of(
            "CET4", "四级",
            "CET6", "六级",
            "IELTS_A", "Academic",
            "IELTS_G", "General");

    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    @Value("${ai.python.essays-dir:src/main/python/OfflineAssistant/backend/data/essays}")
    private String essaysDir;
    @Value("${ai.python.vector-cache-dir:src/main/python/OfflineAssistant/backend/data/vector_store}")
    private String cacheDir;

    private final List<EssayDoc> essays = new ArrayList<>();
    private final List<float[]> vectors = new ArrayList<>();
    private volatile boolean loaded = false;

    public EssayVectorStore(AiGateway aiGateway, ObjectMapper objectMapper) {
        this.aiGateway = aiGateway;
        this.objectMapper = objectMapper;
    }

    /**
     * 返回与作文最相似的 top-k 范文；服务异常/无范文时返回空列表（不抛异常）。
     */
    public List<Map<String, Object>> findSimilar(String text, String examType, int topK) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            ensureLoaded();
        } catch (Exception e) {
            log.warn("范文向量加载失败: {}", e.getMessage());
            return List.of();
        }
        if (essays.isEmpty() || vectors.size() != essays.size()) {
            return List.of();
        }

        float[] queryVec;
        try {
            List<float[]> embedded = aiGateway.embed(List.of(text));
            if (embedded.isEmpty()) {
                return List.of();
            }
            queryVec = embedded.get(0);
        } catch (Exception e) {
            log.warn("当前作文向量化失败（8082 不可用？）: {}", e.getMessage());
            return List.of();
        }

        String targetLevel = EXAM_TO_LEVEL.get(examType);
        List<Map<String, Object>> scored = new ArrayList<>();
        for (int i = 0; i < essays.size(); i++) {
            EssayDoc doc = essays.get(i);
            // 只检索匹配考试类型的范文；level 不匹配则跳过
            if (targetLevel != null && !targetLevel.equals(doc.level)) {
                continue;
            }
            double sim = aiGateway.similarity(queryVec, vectors.get(i));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", doc.id);
            item.put("title", doc.title);
            item.put("topic", doc.topic);
            item.put("level", doc.level);
            item.put("content", doc.content);
            item.put("score", doc.score);
            item.put("similarity", Math.round(sim * 10000.0) / 10000.0);
            scored.add(item);
        }
        scored.sort(Comparator.comparingDouble(
                (Map<String, Object> m) -> ((Number) m.get("similarity")).doubleValue()).reversed());
        return scored.size() <= topK ? scored : new ArrayList<>(scored.subList(0, topK));
    }

    // ------------------------------------------------------------------
    // 加载 / 缓存
    // ------------------------------------------------------------------

    private synchronized void ensureLoaded() throws Exception {
        if (loaded) {
            return;
        }
        File dir = new File(essaysDir);
        if (!dir.isDirectory()) {
            log.warn("范文目录不存在: {}", dir.getAbsolutePath());
            loaded = true;
            return;
        }
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));
        if (files != null) {
            // 与 Python sorted(glob) 一致：按文件名排序，保证指纹稳定
            List<File> sorted = new ArrayList<>(List.of(files));
            sorted.sort(Comparator.comparing(File::getName));
            for (File f : sorted) {
                try {
                    JsonNode arr = objectMapper.readTree(f);
                    if (arr.isArray()) {
                        int before = essays.size();
                        for (JsonNode node : arr) {
                            essays.add(new EssayDoc(
                                    text(node, "id"),
                                    text(node, "title"),
                                    text(node, "topic"),
                                    text(node, "level"),
                                    node.path("score").isNull() ? null : node.path("score").asDouble(),
                                    text(node, "content")));
                        }
                        log.info("从 {} 加载 {} 篇范文", f.getName(), essays.size() - before);
                    }
                } catch (Exception e) {
                    log.error("加载范文失败 {}: {}", f.getName(), e.getMessage());
                }
            }
        }
        if (essays.isEmpty()) {
            loaded = true;
            return;
        }

        Path cachePath = Path.of(cacheDir);
        Files.createDirectories(cachePath);
        String fingerprint = fingerprint();
        File cacheFile = cachePath.resolve("fanwen_" + fingerprint + ".json").toFile();

        if (cacheFile.exists()) {
            loadCache(cacheFile);
            log.info("从缓存加载向量 {} 条（{}）", vectors.size(), cacheFile.getName());
        } else {
            // 清理旧指纹缓存（与原版一致）
            File[] oldCaches = cachePath.toFile().listFiles((d, n) -> n.startsWith("fanwen_") && n.endsWith(".json"));
            if (oldCaches != null) {
                for (File old : oldCaches) {
                    //noinspection ResultOfMethodCallIgnored
                    old.delete();
                }
            }
            List<String> texts = essays.stream().map(e -> e.content).toList();
            vectors.addAll(aiGateway.embed(texts));
            // 缓存为 List<List<Double>>，格式与 Python json.dump 完全一致
            objectMapper.writeValue(cacheFile, vectors.stream().map(EssayVectorStore::toDoubles).toList());
            log.info("向量化完成并缓存 {} 条 → {}", vectors.size(), cacheFile.getName());
        }
        loaded = true;
    }

    private void loadCache(File cacheFile) throws Exception {
        JsonNode arr = objectMapper.readTree(cacheFile);
        for (JsonNode vec : arr) {
            float[] v = new float[vec.size()];
            for (int i = 0; i < vec.size(); i++) {
                v[i] = (float) vec.get(i).asDouble();
            }
            vectors.add(v);
        }
    }

    /** 内容指纹：MD5(content1|content2|...) 前 12 位（与原版 _cache_key 一致）。 */
    private String fingerprint() throws Exception {
        String joined = String.join("|", essays.stream().map(e -> e.content).toList());
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(joined.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.substring(0, 12);
    }

    private static List<Double> toDoubles(float[] v) {
        List<Double> list = new ArrayList<>(v.length);
        for (float f : v) {
            list.add((double) f);
        }
        return list;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText();
    }

    /** 范文文档（字段与 data/essays/*.json 对齐）。 */
    private record EssayDoc(String id, String title, String topic, String level,
                            Double score, String content) {
    }
}
