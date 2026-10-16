package com.amilingo.platform.module.lang.service;

import com.amilingo.platform.common.util.PetUtil;
import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.module.lang.ai.SpeakingScorer;
import com.amilingo.platform.module.lang.ai.WritingScorer;
import com.amilingo.platform.module.lang.dto.request.QuizApiRequest;
import com.amilingo.platform.module.lang.dto.request.SpeakingApiRequest;
import com.amilingo.platform.module.lang.dto.request.WritingGradeApiRequest;
import com.amilingo.platform.module.lang.dto.request.WritingImageApiRequest;
import com.amilingo.platform.module.lang.dto.response.WritingGradeResponse;
import com.amilingo.platform.module.lang.dto.response.WritingGradeView;
import com.amilingo.platform.module.lang.entity.ExamGoal;
import com.amilingo.platform.module.lang.entity.PracticeAttempt;
import com.amilingo.platform.module.lang.entity.PracticeQuestion;
import com.amilingo.platform.module.lang.entity.ScoreTarget;
import com.amilingo.platform.module.lang.enums.ExamType;
import com.amilingo.platform.module.lang.enums.Section;
import com.amilingo.platform.module.lang.repository.ExamGoalRepository;
import com.amilingo.platform.module.lang.repository.PracticeAttemptRepository;
import com.amilingo.platform.module.lang.repository.PracticeQuestionRepository;
import com.amilingo.platform.module.lang.repository.ScoreTargetRepository;
import com.amilingo.platform.module.pet.service.PetService;
import com.amilingo.platform.module.user.entity.user.LearningRecord;
import com.amilingo.platform.module.user.entity.user.User;
import com.amilingo.platform.module.user.repository.LearningRecordRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * 写作 / 口语 / 选择题练习编排服务：调用评分器、写练习流水（{@link PracticeAttempt}）
 * 与学习流水（{@link LearningRecord}）、发宠物经验。AI 评分只在此层编排，Scorer 不做持久化。
 */
@Service
public class WritingService {

    private final WritingScorer writingScorer;
    private final SpeakingScorer speakingScorer;
    private final PracticeAttemptRepository practiceAttemptRepository;
    private final PracticeQuestionRepository practiceQuestionRepository;
    private final LearningRecordRepository learningRecordRepository;
    private final ExamGoalRepository examGoalRepository;
    private final ScoreTargetRepository scoreTargetRepository;
    private final PetService petService;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    /** 本地 FastAPI 编排层（图片作文多模态识别+评分），仅 ai.provider=python 时可用。 */
    @Value("${ai.python.base-url:http://127.0.0.1:8000}")
    private String pythonBaseUrl;

    private final HttpClient visionHttpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public WritingService(WritingScorer writingScorer,
            SpeakingScorer speakingScorer,
            PracticeAttemptRepository practiceAttemptRepository,
            PracticeQuestionRepository practiceQuestionRepository,
            LearningRecordRepository learningRecordRepository,
            ExamGoalRepository examGoalRepository,
            ScoreTargetRepository scoreTargetRepository,
            PetService petService,
            EntityManager entityManager,
            ObjectMapper objectMapper) {
        this.writingScorer = writingScorer;
        this.speakingScorer = speakingScorer;
        this.practiceAttemptRepository = practiceAttemptRepository;
        this.practiceQuestionRepository = practiceQuestionRepository;
        this.learningRecordRepository = learningRecordRepository;
        this.examGoalRepository = examGoalRepository;
        this.scoreTargetRepository = scoreTargetRepository;
        this.petService = petService;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WritingGradeView gradeWriting(Long userId, WritingGradeApiRequest api) {
        ExamGoal goal = examGoalRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        Map<String, Double> targetParts = loadTargetParts(userId, goal);

        com.amilingo.platform.module.lang.dto.request.WritingGradeRequest internal = new com.amilingo.platform.module.lang.dto.request.WritingGradeRequest(
                api.examType(),
                api.topic(),
                api.content(),
                goal != null ? goal.getTargetTotal() : null,
                targetParts);

        WritingGradeResponse response = writingScorer.score(internal);

        int durationMinutes = estimateWritingMinutes(response.wordCount());
        persistAttempt(userId, response, durationMinutes);
        grantRewards(userId, Section.WRITING, response.overall(),
                ExamType.fromCode(api.examType()).getMaxScore(), durationMinutes,
                "写作练习：" + abbreviate(api.topic()));

        return new WritingGradeView(
                response.overall(),
                toDoubleScores(response.dimensions()),
                response.band(),
                response.bandComment(),
                response.nextActions(),
                response.dimensionComments(),
                response.sentences(),
                null,
                response.wordCount(),
                response.basicIssues(),
                response.feedback(),
                response.summary(),
                response.similarEssays());
    }

    /**
     * 图片作文评分：转发给本地 FastAPI 编排层（Gemma 以 --mmproj 启动，单次调用完成 OCR 识别+评分）。
     * 返回结构与文本评分视图一致，content 为模型识别出的作文原文（前端回填到文本框）。
     * 图片识别推理约 2-5 分钟，此路径不落练习流水。
     */
    public WritingGradeView gradeWritingImage(Long userId, WritingImageApiRequest api) {
        if (api.imageBase64() == null || api.imageBase64().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "图片内容为空");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("image_base64", api.imageBase64());
        body.put("topic", api.topic());
        body.put("level", api.level() != null ? api.level() : "Academic");
        body.put("exam_type", api.examType() != null ? api.examType() : "IELTS_A");
        body.put("image_type", api.imageType() != null ? api.imageType() : "jpeg");

        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "构造图片评分请求失败: " + e.getMessage());
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pythonBaseUrl.replaceAll("/+$", "") + "/api/v1/writing/grade-image"))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response;
        try {
            response = visionHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "无法连接本地图片评分服务（FastAPI 8000 / 多模态 llama-server 8081）: " + e.getMessage());
        }
        if (response.statusCode() / 100 != 2) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "图片评分服务返回 " + response.statusCode() + ": " + response.body());
        }
        try {
            var root = objectMapper.readTree(response.body());
            if (!root.path("success").asBoolean(false)) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        root.path("message").asText("图片评分失败"));
            }
            var d = root.path("data");
            Map<String, Double> scores = new LinkedHashMap<>();
            d.path("scores").forEachEntry((k, v) -> {
                if (v.isNumber()) {
                    scores.put(k, v.asDouble());
                }
            });
            List<String> nextActions = new ArrayList<>();
            d.path("next_actions").forEach(n -> nextActions.add(n.asText()));
            Map<String, String> dimensionComments = new LinkedHashMap<>();
            d.path("dimension_comments").forEachEntry((k, v) -> dimensionComments.put(k, v.asText()));
            List<Map<String, Object>> sentences = new ArrayList<>();
            d.path("sentences").forEach(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                s.forEachEntry((k, v) -> m.put(k, v.asText()));
                sentences.add(m);
            });
            // basic_issues（FastAPI 图片模式同样返回词数等基础检查）
            List<Map<String, Object>> basicIssues = new ArrayList<>();
            d.path("basic_issues").forEach(item -> {
                Map<String, Object> m = new LinkedHashMap<>();
                item.forEachEntry((k, v) -> m.put(k, v.asText()));
                basicIssues.add(m);
            });
            String overallComment = d.path("overall_comment").asText("");
            if (overallComment.isBlank()) {
                overallComment = d.path("feedback").asText("");
            }
            // 图片模式原版不做逐句轮/范文检索（避免两次长耗时推理），summary/similar_essays 留空
            return new WritingGradeView(
                    d.path("total_score").asDouble(),
                    scores,
                    d.path("band").asText(""),
                    d.path("band_comment").asText(""),
                    nextActions,
                    dimensionComments,
                    sentences,
                    d.path("content").asText(""),
                    d.path("word_count").asInt(0),
                    basicIssues,
                    overallComment,
                    "",
                    List.of());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "图片评分响应解析失败: " + e.getMessage());
        }
    }

    @Transactional
    public SpeakingScorer.SpeakingGradeResult gradeSpeaking(Long userId, SpeakingApiRequest api) {
        SpeakingScorer.SpeakingGradeResult result = speakingScorer.score(api.transcript(), api.topic());

        int durationMinutes = Math.max(1, estimateWritingMinutes(
                api.transcript() != null ? api.transcript().trim().split("\\s+").length : 0));

        PracticeAttempt attempt = PracticeAttempt.builder()
                .userId(userId)
                .examType(ExamType.IELTS_A)
                .section(Section.SPEAKING)
                .userAnswer(api.transcript())
                .overall(result.overall())
                .scores(writeJson(result.scores()))
                .feedback(result.feedback())
                .durationSeconds(durationMinutes * 60)
                .build();
        practiceAttemptRepository.save(attempt);

        grantRewards(userId, Section.SPEAKING, result.overall(), 9.0, durationMinutes,
                "口语练习：" + abbreviate(api.topic()));
        return result;
    }

    @Transactional
    public Map<String, Object> submitQuiz(Long userId, QuizApiRequest api) {
        PracticeQuestion question = api.questionId() != null
                ? practiceQuestionRepository.findById(api.questionId()).orElse(null)
                : null;

        boolean correct = question != null && question.getAnswer() != null
                && question.getAnswer().trim().equalsIgnoreCase(
                        api.answer() != null ? api.answer().trim() : "");

        ExamType examType = api.examType() != null ? ExamType.fromCode(api.examType()) : ExamType.IELTS_A;
        Section section = api.section() != null ? Section.fromCode(api.section()) : Section.READING;

        // overall 置 null：单题对错不参与薄弱点均分，仅用于错题闭环
        PracticeAttempt attempt = PracticeAttempt.builder()
                .userId(userId)
                .questionId(api.questionId())
                .examType(examType)
                .section(section)
                .userAnswer(api.answer())
                .overall(null)
                .durationSeconds(api.durationSeconds())
                .feedback(correct ? "回答正确"
                        : "回答错误，正确答案：" +
                                (question != null ? question.getAnswer() : "未知"))
                .build();
        practiceAttemptRepository.save(attempt);

        if (!correct) {
            LearningRecord record = LearningRecord.builder()
                    .user(referenceUser(userId))
                    .zone(LearningRecord.Zone.LANG)
                    .taskName(section.getDisplayName() + "错题复盘")
                    .score((byte) 0)
                    .durationMinutes(1)
                    .build();
            learningRecordRepository.save(record);
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("correct", correct);
        view.put("correctAnswer", question != null ? question.getAnswer() : null);
        view.put("attemptId", attempt.getId());
        return view;
    }

    // ------------------------------------------------------------------
    private void persistAttempt(Long userId, WritingGradeResponse response, int durationMinutes) {
        PracticeAttempt attempt = PracticeAttempt.builder()
                .userId(userId)
                .examType(ExamType.fromCode(response.examType()))
                .section(Section.WRITING)
                .overall(response.overall())
                .scores(writeJson(response.dimensions()))
                .feedback(response.feedback())
                .durationSeconds(durationMinutes * 60)
                .build();
        practiceAttemptRepository.save(attempt);
    }

    private void grantRewards(Long userId, Section section, double overall, double maxScore,
            int durationMinutes, String taskName) {
        int score100 = (int) Math.round(Math.max(0, Math.min(1, overall / maxScore)) * 100);

        LearningRecord record = LearningRecord.builder()
                .user(referenceUser(userId))
                .zone(LearningRecord.Zone.LANG)
                .taskName(taskName)
                .score((byte) score100)
                .durationMinutes(durationMinutes)
                .build();
        learningRecordRepository.save(record);

        long exp = PetUtil.calcExp(score100, durationMinutes);
        petService.grantExperience(userId, exp);
    }

    private Map<String, Double> loadTargetParts(Long userId, ExamGoal goal) {
        Map<String, Double> parts = new LinkedHashMap<>();
        if (goal != null) {
            List<ScoreTarget> targets = scoreTargetRepository.findByUserIdAndGoalId(userId, goal.getId());
            for (ScoreTarget target : targets) {
                parts.put(target.getSection().getCode(), target.getTargetScore());
            }
        }
        return parts;
    }

    private static Map<String, Double> toDoubleScores(Map<String, Object> dimensions) {
        Map<String, Double> scores = new LinkedHashMap<>();
        if (dimensions != null) {
            for (Map.Entry<String, Object> e : dimensions.entrySet()) {
                Object v = e.getValue();
                if (v instanceof Number number) {
                    scores.put(e.getKey(), number.doubleValue());
                }
            }
        }
        return scores;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private User referenceUser(Long userId) {
        return entityManager.getReference(User.class, userId);
    }

    private static int estimateWritingMinutes(Integer wordCount) {
        int words = wordCount != null ? wordCount : 0;
        return Math.max(5, words / 12);
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 30 ? text : text.substring(0, 30);
    }
}
