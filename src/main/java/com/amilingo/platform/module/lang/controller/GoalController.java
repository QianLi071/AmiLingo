package com.amilingo.platform.module.lang.controller;

import com.amilingo.platform.common.config.security.SecurityUtil;
import com.amilingo.platform.common.dto.ApiResponse;
import com.amilingo.platform.module.lang.dto.request.GoalRequest;
import com.amilingo.platform.module.lang.dto.response.GoalView;
import com.amilingo.platform.module.lang.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 考试目标接口。 */
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    public ApiResponse<GoalView> getGoal() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(goalService.getGoal(userId));
    }

    @PutMapping
    public ApiResponse<GoalView> saveGoal(@Valid @RequestBody GoalRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("目标已保存", goalService.saveGoal(userId, request));
    }
}
