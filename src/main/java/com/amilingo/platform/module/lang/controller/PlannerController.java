package com.amilingo.platform.module.lang.controller;

import com.amilingo.platform.common.config.security.SecurityUtil;
import com.amilingo.platform.common.dto.ApiResponse;
import com.amilingo.platform.module.lang.dto.request.PlannerRequest;
import com.amilingo.platform.module.lang.dto.response.PlanView;
import com.amilingo.platform.module.lang.service.PlannerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 排程接口。 */
@RestController
@RequestMapping("/api/v1/planner")
public class PlannerController {

    private final PlannerService plannerService;

    public PlannerController(PlannerService plannerService) {
        this.plannerService = plannerService;
    }

    @PostMapping("/generate")
    public ApiResponse<PlanView> generate(@RequestBody PlannerRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("计划已生成", plannerService.generate(userId, request));
    }

    @GetMapping("/current")
    public ApiResponse<PlanView> current() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(plannerService.getCurrentPlan(userId));
    }

    @PostMapping("/tasks/{taskId}/toggle")
    public ApiResponse<PlanView> toggleTask(@PathVariable Long taskId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(plannerService.toggleTask(userId, taskId));
    }
}
