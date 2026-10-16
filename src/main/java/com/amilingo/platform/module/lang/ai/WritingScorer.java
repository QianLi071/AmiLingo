package com.amilingo.platform.module.lang.ai;

import com.amilingo.platform.infra.ai.AiGateway;
import com.amilingo.platform.module.lang.ai.prompt.WritingPrompt;
import com.amilingo.platform.module.lang.dto.request.WritingGradeRequest;
import com.amilingo.platform.module.lang.dto.response.WritingGradeResponse;
import com.amilingo.platform.module.lang.enums.ExamType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 写作评分器：严格对齐原版 Python {@code writing_service.WritingService.grade_essay} 的多模型多轮流程。
 *
 * <ol>
 *   <li>{@code _basic_check}   本地规则：字数区间 + 模板句检测（不走模型）；</li>
 *   <li>{@code _get_grade}     第 1 轮调用 Gemma（8081）：总分/档位/维度分/点评/改进动作；</li>
 *   <li>{@code _get_suggestions} 第 2 轮调用 Gemma（8081）：逐句 issues/revised/comment + summary；</li>
 *   <li>{@code _find_similar_essays} 调用 Qwen 向量模型（8082）：同 level 范文余弦检索 top2。</li>
 * </ol>
 *
 * 评分轮/逐句轮任一步失败均降级为空结果并写入提示（与原版 try/except 一致），不中断整个请求。
 */
@Slf4j
@Component
public class WritingScorer {

    /** 两轮生成的采样参数（与原版 ai_client.chat 调用一致）。 */
    private static final double TEMPERATURE = 0.7;
    private static final int MAX_TOKENS = 2048;
    private static final int SIMILAR_TOP_K = 2;

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);

    /** 各考试类型的维度中文标签（对齐原版 DIMENSION_LABELS）。 */
    private static final Map<String, String> CET_DIM_LABELS = Map.ofEntries(
            Map.entry("content", "内容切题"),
            Map.entry("structure", "结构清晰"),
            Map.entry("logic", "逻辑连贯"),
            Map.entry("grammar", "语法准确"),
            Map.entry("vocabulary", "词汇丰富"),
            Map.entry("sentence_variety", "句式多样"),
            Map.entry("naturalness", "表达自然"),
            Map.entry("template_trace", "原创程度"));
    private static final Map<String, String> IELTS_DIM_LABELS = Map.of(
            "TR", "任务回应",
            "CC", "连贯衔接",
            "LR", "词汇资源",
            "GRA", "语法多样与准确");

    /** 模板化表达黑名单（小写匹配，对齐原版 _basic_check）。 */
    private static final List<String> TEMPLATE_PATTERNS = List.of(
            "with the development of",
            "as far as i am concerned",
            "every coin has two sides");

    private final AiGateway aiGateway;
    private final EssayVectorStore essayVectorStore;
    private final ObjectMapper objectMapper;

    public WritingScorer(AiGateway aiGateway, EssayVectorStore essayVectorStore, ObjectMapper objectMapper) {
        this.aiGateway = aiGateway;
        this.essayVectorStore = essayVectorStore;
        this.objectMapper = objectMapper;
    }

    public WritingGradeResponse score(WritingGradeRequest req) {
        ExamType examType = ExamType.fromCode(req.examType());
        // {level} 占位符：四级 / 六级 / Academic / General（对齐原版入参语义）
        String level = examType.getLevel();
        String topic = req.prompt() != null ? req.prompt() : "";
        String essay = req.essay() != null ? req.essay() : "";

        // ① 本地基础检查
        Map<String, Object> basicCheck = basicCheck(essay, examType);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> basicIssues = (List<Map<String, Object>>) basicCheck.get("issues");
        int wordCount = (int) basicCheck.get("word_count");

        // ② 第 1 轮 Gemma：评分
        GradeResult grade = getGrade(essay, topic, level, examType);

        // ③ 第 2 轮 Gemma：逐句批改
        SuggestionResult suggestions = getSuggestions(essay, topic, level, examType);

        // ④ Qwen 8082：范文检索
        List<Map<String, Object>> similarEssays =
                essayVectorStore.findSimilar(essay, examType.name(), SIMILAR_TOP_K);

        return new WritingGradeResponse(
                examType.name(),
                topic,
                level,
                wordCount,
                basicIssues,

                // ---- 总分 ----
                grade.totalScore,
                grade.band,
                grade.bandComment,
                grade.total106,
                grade.totalScorePercent,

                // ---- 诊断 ----
                new LinkedHashMap<>(grade.scores),
                grade.dimensionLabels,
                grade.dimensionComments,
                grade.overallComment,
                grade.nextActions,

                // ---- 逐句批改 ----
                suggestions.sentences,
                suggestions.summary,

                // ---- 范文 ----
                similarEssays,

                OffsetDateTime.now().toString()
        );
    }

    // ==================================================================
    // ① 基础规则检查（原版 _basic_check）
    // ==================================================================
    private Map<String, Object> basicCheck(String content, ExamType examType) {
        int wordCount = content.isBlank() ? 0 : content.trim().split("\\s+").length;
        List<Map<String, Object>> issues = new ArrayList<>();

        int minW;
        int maxW;
        switch (examType) {
            case CET4 -> { minW = 120; maxW = 180; }
            case CET6 -> { minW = 150; maxW = 200; }
            default -> { minW = 250; maxW = 300; }   // IELTS_A / IELTS_G 及其他
        }
        if (wordCount < minW) {
            issues.add(Map.of("type", "word_count",
                    "message", "词数不足（当前" + wordCount + "词，要求" + minW + "-" + maxW + "词）"));
        } else if (wordCount > maxW) {
            issues.add(Map.of("type", "word_count",
                    "message", "词数过多（当前" + wordCount + "词，要求" + minW + "-" + maxW + "词）"));
        }

        String lower = content.toLowerCase();
        List<String> found = TEMPLATE_PATTERNS.stream().filter(lower::contains).toList();
        if (!found.isEmpty()) {
            issues.add(Map.of(
                    "type", "template",
                    "message", "检测到模板化表达: " + String.join(", ", found),
                    "suggestion", "建议用更自然的表达替代模板句"));
        }

        return Map.of("word_count", wordCount, "issues", issues);
    }

    // ==================================================================
    // ② 评分轮（原版 _get_grade）
    // ==================================================================
    private GradeResult getGrade(String essay, String topic, String level, ExamType examType) {
        String prompt = WritingPrompt.fill(WritingPrompt.getGradePrompt(examType), level, topic, essay);
        try {
            String raw = aiGateway.chatJsonUser(prompt, TEMPERATURE, MAX_TOKENS);
            JsonNode data = parseJson(raw);

            Map<String, Double> scores = readScoreMap(data.path("scores"));
            double totalScore = round1(data.path("total_score").asDouble(0));
            // 兜底：模型没给总分时用维度平均
            if (totalScore == 0 && !scores.isEmpty()) {
                totalScore = round1(scores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0));
            }

            String band = textOrEmpty(data, "band");
            String bandComment = textOrEmpty(data, "band_comment");
            // 档位缺失时代码判定：四六级用官方 5 档，IELTS 用英文档名
            if (band.isBlank()) {
                String[] classified = classifyBand(totalScore, examType);
                band = classified[0];
                bandComment = classified[1];
            }

            return new GradeResult(
                    scores,
                    totalScore,
                    band,
                    bandComment,
                    round1(totalScore / 15 * 106.5),
                    round1(totalScore / 15 * 100),
                    dimLabels(examType),
                    readStringMap(data.path("dimension_comments")),
                    textOrEmpty(data, "overall_comment"),
                    readStringList(data.path("next_actions")));
        } catch (Exception e) {
            log.error("评分轮调用失败: {}", e.getMessage());
            return GradeResult.empty("AI 评分解析失败，请稍后重试", examType);
        }
    }

    // ==================================================================
    // ③ 逐句批改轮（原版 _get_suggestions）
    // ==================================================================
    private SuggestionResult getSuggestions(String essay, String topic, String level, ExamType examType) {
        String prompt = WritingPrompt.fill(WritingPrompt.getSuggestionPrompt(examType), level, topic, essay);
        try {
            String raw = aiGateway.chatJsonUser(prompt, TEMPERATURE, MAX_TOKENS);
            JsonNode data = parseJson(raw);

            List<Map<String, Object>> sentences = new ArrayList<>();
            data.path("sentences").forEach(node -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", node.path("index").asInt(0));
                item.put("original", textOrEmpty(node, "original"));
                List<String> issues = new ArrayList<>();
                node.path("issues").forEach(i -> issues.add(i.asText()));
                item.put("issues", issues);
                item.put("revised", textOrEmpty(node, "revised"));
                item.put("comment", textOrEmpty(node, "comment"));
                sentences.add(item);
            });
            return new SuggestionResult(sentences, textOrEmpty(data, "summary"));
        } catch (Exception e) {
            log.error("逐句批改轮调用失败: {}", e.getMessage());
            return new SuggestionResult(List.of(), "AI 批改建议生成失败，请稍后重试");
        }
    }

    // ==================================================================
    // 档位判定（四六级照搬原版 _classify_band；IELTS 用官方英文档名）
    // ==================================================================
    private String[] classifyBand(double score, ExamType examType) {
        if (examType == ExamType.CET4 || examType == ExamType.CET6) {
            if (score >= 13) {
                return new String[]{"13-15档", "切题，表达思想清楚，文字通顺、连贯，基本无语言错误"};
            }
            if (score >= 10) {
                return new String[]{"10-12档", "切题，表达思想清楚，文字连贯，但有少量语言错误"};
            }
            if (score >= 7) {
                return new String[]{"7-9档", "基本切题，有些地方表达不够清楚，文字勉强连贯"};
            }
            if (score >= 4) {
                return new String[]{"4-6档", "基本切题，表达思想不清楚，连贯性差，有较多语言错误"};
            }
            if (score >= 1) {
                return new String[]{"1-3档", "条理不清，思路紊乱，语言支离破碎"};
            }
            return new String[]{"0分", "白卷或所写内容与题目无关"};
        }
        return ieltsBand(score);
    }

    private String[] ieltsBand(double overall) {
        if (overall >= 8.5) {
            return new String[]{"Expert User", "完全掌握英语，运用自如、准确流利。"};
        }
        if (overall >= 7.5) {
            return new String[]{"Very Good User", "充分掌握，偶有不准确，整体复杂论证良好。"};
        }
        if (overall >= 6.5) {
            return new String[]{"Good User", "有效掌握，虽有错误但能使用复杂语言。"};
        }
        if (overall >= 5.5) {
            return new String[]{"Competent User", "大致掌握，整体能应对，常犯错误。"};
        }
        if (overall >= 4.5) {
            return new String[]{"Modest User", "部分掌握，可应对熟悉场景，理解有限。"};
        }
        if (overall >= 3.5) {
            return new String[]{"Limited User", "仅限熟悉情境，频繁出现交流障碍。"};
        }
        if (overall >= 2.5) {
            return new String[]{"Extremely Limited User", "交流极困难，难以表达完整意思。"};
        }
        if (overall >= 1.5) {
            return new String[]{"Intermittent User", "除熟悉信息外基本无法使用英语。"};
        }
        return new String[]{"Non User", "未提交或未产生可评估内容。"};
    }

    // ==================================================================
    // JSON 解析 / 小工具
    // ==================================================================

    /** 先直接解析，失败则正则抽取首个 {@code { ... }}（与原版 re.search(r"\{.*\}") 兜底一致）。 */
    private JsonNode parseJson(String raw) throws Exception {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ignored) {
            Matcher m = JSON_OBJECT_PATTERN.matcher(raw);
            if (!m.find()) {
                throw new IllegalArgumentException("模型输出中未找到 JSON 对象");
            }
            return objectMapper.readTree(m.group());
        }
    }

    private static Map<String, Double> readScoreMap(JsonNode node) {
        Map<String, Double> scores = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.forEachEntry((k, v) -> {
                if (v.isNumber()) {
                    scores.put(k, round1(v.asDouble()));
                }
            });
        }
        return scores;
    }

    private static Map<String, String> readStringMap(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.forEachEntry((k, v) -> map.put(k, v.asText("")));
        }
        return map;
    }

    private static List<String> readStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> list.add(n.asText()));
        }
        return list;
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("");
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static Map<String, String> dimLabels(ExamType examType) {
        return (examType == ExamType.CET4 || examType == ExamType.CET6)
                ? CET_DIM_LABELS : IELTS_DIM_LABELS;
    }

    /** 评分轮结果（字段对齐 grade_essay 返回的总分/诊断区块）。 */
    private record GradeResult(
            Map<String, Double> scores,
            double totalScore,
            String band,
            String bandComment,
            double total106,
            double totalScorePercent,
            Map<String, String> dimensionLabels,
            Map<String, String> dimensionComments,
            String overallComment,
            List<String> nextActions) {

        static GradeResult empty(String message, ExamType examType) {
            return new GradeResult(
                    new LinkedHashMap<>(), 0, "", "", 0, 0,
                    dimLabels(examType), new LinkedHashMap<>(), message, List.of());
        }
    }

    /** 逐句轮结果。 */
    private record SuggestionResult(List<Map<String, Object>> sentences, String summary) {
    }
}
