package com.amilingo.platform.module.career.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record JobRiskAssessmentRequest(
        @NotBlank @Size(max = 128) String companyName,
        @PastOrPresent LocalDate registrationDate,
        @DecimalMin("0.0") BigDecimal registeredCapitalAmount,
        @Size(max = 16) String registeredCapitalCurrency,
        @Min(0) Integer employeeCount,
        Boolean verifiedBusinessAddress,
        Boolean officialWebsite,
        Boolean salaryUnusuallyHigh,
        Boolean chargesApplicantFees,
        Boolean requestsSensitiveDataBeforeOffer,
        Boolean usesOnlyPersonalMessaging,
        Boolean jobDescriptionClear,
        Boolean providesWrittenTerms
) {
}
