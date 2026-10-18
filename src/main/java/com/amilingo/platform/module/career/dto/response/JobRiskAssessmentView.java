package com.amilingo.platform.module.career.dto.response;

import com.amilingo.platform.module.career.enums.RiskLevel;
import com.amilingo.platform.module.career.enums.RiskSeverity;

import java.util.List;

public record JobRiskAssessmentView(
        String companyName,
        int riskScore,
        RiskLevel riskLevel,
        List<RiskSignalView> signals,
        List<String> verificationChecklist,
        String disclaimer
) {
    public record RiskSignalView(
            String code,
            RiskSeverity severity,
            String title,
            String explanation
    ) {
    }
}
