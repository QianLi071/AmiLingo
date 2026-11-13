package com.amilingo.platform.module.career.service;

import com.amilingo.platform.module.career.dto.request.JobRiskAssessmentRequest;
import com.amilingo.platform.module.career.dto.response.JobRiskAssessmentView;
import com.amilingo.platform.module.career.enums.RiskLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobRiskAssessmentServiceTest {

    private final JobRiskAssessmentService service = new JobRiskAssessmentService();

    @Test
    void marksApplicantFeesAsHighRisk() {
        JobRiskAssessmentView result = service.assess(request(
                true, true, true, true, false, false, true, true
        ));

        assertEquals(RiskLevel.HIGH, result.riskLevel());
        assertTrue(result.riskScore() >= 50);
        assertTrue(result.signals().stream().anyMatch(signal -> signal.code().equals("APPLICANT_FEE")));
    }

    @Test
    void keepsTransparentRecruitmentLowRisk() {
        JobRiskAssessmentView result = service.assess(request(
                true, true, false, false, false, false, true, true
        ));

        assertEquals(RiskLevel.LOW, result.riskLevel());
        assertEquals(0, result.riskScore());
        assertTrue(result.signals().isEmpty());
    }

    @Test
    void treatsYoungSmallCompanyAsContextRatherThanAutomaticHighRisk() {
        JobRiskAssessmentRequest request = new JobRiskAssessmentRequest(
                "New Studio",
                LocalDate.now().minusMonths(4),
                BigDecimal.ZERO,
                "MOP",
                3,
                true,
                true,
                false,
                false,
                false,
                false,
                true,
                true
        );

        JobRiskAssessmentView result = service.assess(request);

        assertEquals(RiskLevel.LOW, result.riskLevel());
        assertEquals(14, result.riskScore());
        assertEquals(3, result.signals().size());
    }

    private JobRiskAssessmentRequest request(
            Boolean address,
            Boolean website,
            Boolean chargesFees,
            Boolean requestsSensitiveData,
            Boolean personalMessaging,
            Boolean unusuallyHighSalary,
            Boolean clearDescription,
            Boolean writtenTerms
    ) {
        return new JobRiskAssessmentRequest(
                "Example Limited",
                LocalDate.now().minusYears(5),
                new BigDecimal("1000000"),
                "HKD",
                100,
                address,
                website,
                unusuallyHighSalary,
                chargesFees,
                requestsSensitiveData,
                personalMessaging,
                clearDescription,
                writtenTerms
        );
    }
}
