package com.amilingo.platform.module.career.controller;

import com.amilingo.platform.common.dto.ApiResponse;
import com.amilingo.platform.module.career.dto.response.JobDatasetMetadataView;
import com.amilingo.platform.module.career.dto.response.JobPostingView;
import com.amilingo.platform.module.career.enums.JobRegion;
import com.amilingo.platform.module.career.enums.JobType;
import com.amilingo.platform.module.career.service.JobDatasetMetadataService;
import com.amilingo.platform.module.career.service.JobPostingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 内地、香港及澳门的就业与实习岗位接口。 */
@RestController
@RequestMapping("/api/v1/career/jobs")
public class JobPostingController {

    private final JobPostingService jobPostingService;
    private final JobDatasetMetadataService metadataService;

    public JobPostingController(JobPostingService jobPostingService, JobDatasetMetadataService metadataService) {
        this.jobPostingService = jobPostingService;
        this.metadataService = metadataService;
    }

    @GetMapping
    public ApiResponse<List<JobPostingView>> list(
            @RequestParam(required = false) JobRegion region,
            @RequestParam(required = false) JobType jobType,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(jobPostingService.list(region, jobType, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<JobPostingView> get(@PathVariable Long id) {
        return ApiResponse.ok(jobPostingService.get(id));
    }

    @GetMapping("/metadata")
    public ApiResponse<JobDatasetMetadataView> metadata() {
        return ApiResponse.ok(metadataService.get());
    }
}
