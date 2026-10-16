package com.amilingo.platform.module.lang.controller;

import com.amilingo.platform.common.config.security.SecurityUtil;
import com.amilingo.platform.common.dto.ApiResponse;
import com.amilingo.platform.module.lang.ai.SpeakingScorer;
import com.amilingo.platform.module.lang.dto.request.QuizApiRequest;
import com.amilingo.platform.module.lang.dto.request.SpeakingApiRequest;
import com.amilingo.platform.module.lang.dto.request.WritingGradeApiRequest;
import com.amilingo.platform.module.lang.dto.request.WritingImageApiRequest;
import com.amilingo.platform.module.lang.dto.response.WritingGradeView;
import com.amilingo.platform.module.lang.service.WritingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 练习接口：写作评分（前端契约路径）、口语评分、选择题提交。
 */
@RestController
public class PracticeController {

    private final WritingService writingService;

    public PracticeController(WritingService writingService) {
        this.writingService = writingService;
    }

    /** 文本写作评分（前端 api.js 调用路径）。 */
    @PostMapping("/api/v1/writing/grade")
    public ApiResponse<WritingGradeView> grade(@RequestBody WritingGradeApiRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(writingService.gradeWriting(userId, request));
    }

    /**
     * 图片作文评分：转发本地 FastAPI → Gemma 多模态（--mmproj），单次调用完成 OCR 识别 + 评分。
     * 需 llama-server 8081 携带 mmproj、FastAPI 8000 在线；不可用时返回 503 引导手动输入。
     */
    @PostMapping("/api/v1/writing/grade-image")
    public ApiResponse<WritingGradeView> gradeImage(@RequestBody WritingImageApiRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(writingService.gradeWritingImage(userId, request));
    }

    /** 口语评分（转写）。 */
    @PostMapping("/api/v1/practice/speaking")
    public ApiResponse<SpeakingScorer.SpeakingGradeResult> speaking(@RequestBody SpeakingApiRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(writingService.gradeSpeaking(userId, request));
    }

    /** 选择题（听力/阅读）提交。 */
    @PostMapping("/api/v1/practice/quiz")
    public ApiResponse<Map<String, Object>> quiz(@RequestBody QuizApiRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(writingService.submitQuiz(userId, request));
    }
}
