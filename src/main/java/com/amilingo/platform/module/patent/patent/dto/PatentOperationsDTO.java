package com.amilingo.platform.module.patent.dto;
import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.time.LocalDate;import java.util.Map;
/** 专利材料、权利要求、审查意见、期限与费用 DTO。 */
public final class PatentOperationsDTO{private PatentOperationsDTO(){}
 public record DocumentRequest(@NotNull Long actorUserId,Long relatedRecordId,@NotBlank @Pattern(regexp="REQUEST|CLAIMS|SPECIFICATION|ABSTRACT|ABSTRACT_DRAWING|DRAWINGS|DISCLOSURE|FEE_REDUCTION|POWER_OF_ATTORNEY|CORRECTION|OFFICE_ACTION|OFFICE_ACTION_RESPONSE|GRANT_NOTICE|CERTIFICATE|OTHER")String category,
  @NotBlank String fileName,String contentType,@PositiveOrZero Long fileSize,@NotBlank String storageKey,String fileUrl,String fileHash,@Min(1)Integer versionNo){}
 public record ClaimRequest(@NotNull Long actorUserId,@NotNull @Min(1)Integer claimNo,@NotBlank @Pattern(regexp="INDEPENDENT|DEPENDENT")String type,
  @Min(1)Integer parentClaimNo,@NotBlank String content,@Size(max=10000)String technicalFeatures,@Size(max=10000)String supportReference){}
 public record OfficeActionRequest(@NotNull Long actorUserId,@NotBlank @Pattern(regexp="OFFICE_ACTION|CORRECTION|REEXAMINATION|ORAL_HEARING")String actionType,
  @NotNull LocalDate noticeDate,@NotNull LocalDate responseDeadline,@NotBlank String opinionContent,Long assigneeUserId,java.util.List<@NotBlank String>attachments){}
 public record OfficeActionResponseRequest(@NotNull Long actorUserId,@NotBlank String responseContent,Long responseClaimVersionId,java.util.List<@NotBlank String>attachments){}
 public record DeadlineRequest(@NotNull Long actorUserId,Long relatedRecordId,@NotBlank @Pattern(regexp="PUBLICATION|SUBSTANTIVE_EXAM|OFFICE_ACTION_RESPONSE|CORRECTION|PRIORITY|ANNUITY|RESTORATION|INTERNAL")String type,
  @NotBlank @Size(max=255)String title,@NotNull LocalDate dueDate,@Min(0)Integer remindDaysBefore,@Pattern(regexp="LOW|MEDIUM|HIGH|URGENT")String priority,@Size(max=4000)String notes){}
 public record DeadlineStatusRequest(@NotNull Long actorUserId,@NotBlank @Pattern(regexp="PENDING|COMPLETED|CANCELLED")String status){}
 public record DeadlineView(Long id,Long patentId,String type,String title,LocalDate dueDate,long daysRemaining,String urgency,String status){}
 public record FeeRequest(@NotNull Long actorUserId,@NotBlank @Pattern(regexp="APPLICATION|SUBSTANTIVE_EXAM|PRINTING|AGENCY|GRANT_REGISTRATION|ANNUITY|LATE_FEE|OTHER")String type,
  @NotNull @DecimalMin("0.00")BigDecimal amount,String currency,@DecimalMin("0.00")BigDecimal reductionAmount,LocalDate dueDate,@Size(max=4000)String notes){}
 public record FeePaymentRequest(@NotNull Long actorUserId,@NotNull LocalDate paidDate,String receiptDocumentKey){}
 public record FeeSummary(BigDecimal totalPayable,BigDecimal totalPaid,BigDecimal totalUnpaid,Map<String,BigDecimal>byType,String currency){}
}
