package com.amilingo.platform.module.career.service;

import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.module.career.dto.response.JobPostingView;
import com.amilingo.platform.module.career.entity.JobPosting;
import com.amilingo.platform.module.career.enums.JobRegion;
import com.amilingo.platform.module.career.enums.JobType;
import com.amilingo.platform.module.career.repository.JobPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingServiceTest {

    @Mock
    private JobPostingRepository jobPostingRepository;

    private JobPostingService jobPostingService;

    @BeforeEach
    void setUp() {
        jobPostingService = new JobPostingService(jobPostingRepository);
    }

    @Test
    void listNormalizesBlankKeywordAndMapsResults() {
        JobPosting job = sampleJob();
        when(jobPostingRepository.searchActive(JobRegion.HONG_KONG, JobType.INTERNSHIP, null))
                .thenReturn(List.of(job));

        List<JobPostingView> result = jobPostingService.list(
                JobRegion.HONG_KONG,
                JobType.INTERNSHIP,
                "   "
        );

        assertEquals(1, result.size());
        assertEquals("1", result.getFirst().id());
        assertEquals("Software Engineering Intern", result.getFirst().positionTitle());
    }

    @Test
    void getRejectsInactiveOrMissingJob() {
        when(jobPostingRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> jobPostingService.get(99L));

        assertEquals("岗位不存在或已下线", exception.getMessage());
    }

    private JobPosting sampleJob() {
        return JobPosting.builder()
                .id(1L)
                .companyName("Example Limited")
                .positionTitle("Software Engineering Intern")
                .region(JobRegion.HONG_KONG)
                .jobType(JobType.INTERNSHIP)
                .description("Build student-facing software")
                .officialUrl("https://example.com/careers")
                .sourceName("Company careers site")
                .active(true)
                .updatedAt(LocalDateTime.of(2026, 10, 16, 12, 0))
                .build();
    }
}
