package com.amilingo.platform.module.career.service;

import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.module.career.dto.response.JobPostingView;
import com.amilingo.platform.module.career.entity.JobPosting;
import com.amilingo.platform.module.career.enums.JobRegion;
import com.amilingo.platform.module.career.enums.JobType;
import com.amilingo.platform.module.career.repository.JobPostingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;

    public JobPostingService(JobPostingRepository jobPostingRepository) {
        this.jobPostingRepository = jobPostingRepository;
    }

    @Transactional(readOnly = true)
    public List<JobPostingView> list(JobRegion region, JobType jobType, String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        return jobPostingRepository.searchActive(region, jobType, normalizedKeyword)
                .stream()
                .map(JobPostingView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobPostingView get(Long id) {
        JobPosting job = jobPostingRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "岗位不存在或已下线"));
        return JobPostingView.from(job);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}
