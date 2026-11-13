package com.amilingo.platform.module.career.controller;

import com.amilingo.platform.common.dto.ApiResponse;
import com.amilingo.platform.module.career.dto.request.JobFitAssessmentRequest;
import com.amilingo.platform.module.career.dto.response.JobFitAssessmentView;
import com.amilingo.platform.module.career.service.JobFitAssessmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/career/jobs/{jobId}/fit-assessments")
public class JobFitAssessmentController {

    private final JobFitAssessmentService assessmentService;

    public JobFitAssessmentController(JobFitAssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @PostMapping
    public ApiResponse<JobFitAssessmentView> assess(
            @PathVariable Long jobId,
            @Valid @RequestBody JobFitAssessmentRequest request
    ) {
        return ApiResponse.ok(assessmentService.assess(jobId, request));
    }
}
