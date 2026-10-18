package com.amilingo.platform.module.career.dto.response;

import com.amilingo.platform.module.career.entity.JobPosting;
import com.amilingo.platform.module.career.enums.JobRegion;
import com.amilingo.platform.module.career.enums.JobType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record JobPostingView(
        Long id,
        String companyName,
        String positionTitle,
        JobRegion region,
        JobType jobType,
        String industry,
        String location,
        String description,
        String requirements,
        LocalDate applicationDeadline,
        String officialUrl,
        String sourceName,
        LocalDateTime updatedAt
) {
    public static JobPostingView from(JobPosting job) {
        return new JobPostingView(
                job.getId(),
                job.getCompanyName(),
                job.getPositionTitle(),
                job.getRegion(),
                job.getJobType(),
                job.getIndustry(),
                job.getLocation(),
                job.getDescription(),
                job.getRequirements(),
                job.getApplicationDeadline(),
                job.getOfficialUrl(),
                job.getSourceName(),
                job.getUpdatedAt()
        );
    }
}
