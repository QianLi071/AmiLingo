package com.amilingo.platform.module.career.service;

import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.module.career.dto.request.JobFitAssessmentRequest;
import com.amilingo.platform.module.career.dto.response.JobFitAssessmentView;
import com.amilingo.platform.module.career.entity.JobPosting;
import com.amilingo.platform.module.career.enums.JobFitLevel;
import com.amilingo.platform.module.career.enums.JobRegion;
import com.amilingo.platform.module.career.enums.JobType;
import com.amilingo.platform.module.career.repository.JobPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobFitAssessmentServiceTest {

    @Mock
    private JobPostingRepository repository;

    private JobFitAssessmentService service;

    @BeforeEach
    void setUp() {
        service = new JobFitAssessmentService(repository);
    }

    @Test
    void identifiesStrongMatchWithTraceableEvidence() {
        JobPosting job = sampleJob();
        JobFitAssessmentRequest request = new JobFitAssessmentRequest(
                "计算机科学与技术",
                List.of("Java", "Python", "人工智能"),
                "课程项目中使用 Java 和 Python 开发人工智能应用",
                JobRegion.MAINLAND_CHINA,
                JobType.INTERNSHIP,
                "负责 AI 应用研发，使用 Java、Python 完成算法服务。要求计算机或软件相关专业，重视项目实践和团队协作。"
                        .repeat(3)
        );

        JobFitAssessmentView result = service.assess(job, request);

        assertEquals(JobFitLevel.STRONG_MATCH, result.fitLevel());
        assertEquals("DETAILED", result.informationQuality());
        assertTrue(result.referenceScore() >= 75);
        assertTrue(result.matchedEvidence().stream().anyMatch(text -> text.contains("Java")));
        assertEquals(5, result.criteria().size());
    }

    @Test
    void keepsResultCautiousWhenOfficialDescriptionIsLimited() {
        JobPosting job = sampleJob();
        JobFitAssessmentRequest request = new JobFitAssessmentRequest(
                "历史学",
                List.of("Excel"),
                null,
                null,
                null,
                null
        );

        JobFitAssessmentView result = service.assess(job, request);

        assertEquals("LIMITED", result.informationQuality());
        assertEquals(JobFitLevel.NEEDS_REVIEW, result.fitLevel());
        assertTrue(result.summary().contains("初步参考"));
        assertTrue(result.itemsToVerify().stream().anyMatch(text -> text.contains("完整职位说明")));
        assertFalse(result.nextActions().isEmpty());
    }

    @Test
    void rejectsMissingOrInactiveJob() {
        when(repository.findByIdAndActiveTrue(404L)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> service.assess(
                404L,
                new JobFitAssessmentRequest("计算机", List.of("Java"), null, null, null, null)
        ));

        assertEquals("岗位不存在或已下线", exception.getMessage());
    }

    private JobPosting sampleJob() {
        return JobPosting.builder()
                .id(10L)
                .companyName("Example Technology")
                .positionTitle("AI 应用开发实习生")
                .region(JobRegion.MAINLAND_CHINA)
                .jobType(JobType.INTERNSHIP)
                .industry("Internet / Technology")
                .description("企业官网公开岗位，详细内容请查看官网。")
                .requirements("申请条件以企业官网为准。")
                .officialUrl("https://example.com/job/10")
                .sourceName("Official careers site")
                .active(true)
                .build();
    }
}
