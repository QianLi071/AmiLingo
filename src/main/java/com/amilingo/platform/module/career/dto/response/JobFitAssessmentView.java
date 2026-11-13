package com.amilingo.platform.module.career.dto.response;

import com.amilingo.platform.module.career.enums.JobFitLevel;

import java.util.List;

public record JobFitAssessmentView(
        String jobId,
        String companyName,
        String positionTitle,
        int referenceScore,
        JobFitLevel fitLevel,
        String informationQuality,
        String summary,
        List<FitCriterionView> criteria,
        List<String> matchedEvidence,
        List<String> itemsToVerify,
        List<String> nextActions,
        String disclaimer
) {
    public record FitCriterionView(
            String name,
            int score,
            int maxScore,
            String explanation
    ) {
    }
}
