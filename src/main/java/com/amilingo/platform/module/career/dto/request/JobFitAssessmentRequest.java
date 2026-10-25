package com.amilingo.platform.module.career.dto.request;

import com.amilingo.platform.module.career.enums.JobRegion;
import com.amilingo.platform.module.career.enums.JobType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record JobFitAssessmentRequest(
        @NotBlank @Size(max = 128) String major,
        @Size(max = 30) List<@NotBlank @Size(max = 64) String> skills,
        @Size(max = 2000) String experienceSummary,
        JobRegion preferredRegion,
        JobType preferredJobType,
        @Size(max = 10000) String fullJobDescription
) {
}
