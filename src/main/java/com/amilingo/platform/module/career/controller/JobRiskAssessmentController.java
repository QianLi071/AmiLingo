package com.amilingo.platform.module.career.controller;

import com.amilingo.platform.common.dto.ApiResponse;
import com.amilingo.platform.module.career.dto.request.JobRiskAssessmentRequest;
import com.amilingo.platform.module.career.dto.response.JobRiskAssessmentView;
import com.amilingo.platform.module.career.service.JobRiskAssessmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/career/risk-assessments")
public class JobRiskAssessmentController {

    private final JobRiskAssessmentService assessmentService;

    public JobRiskAssessmentController(JobRiskAssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @PostMapping
    public ApiResponse<JobRiskAssessmentView> assess(@Valid @RequestBody JobRiskAssessmentRequest request) {
        return ApiResponse.ok(assessmentService.assess(request));
    }
}
