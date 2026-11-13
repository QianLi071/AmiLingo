package com.amilingo.platform.module.patent.dto;
import jakarta.validation.constraints.*;import java.time.LocalDate;import java.util.List;
/** 专利交底、状态、项目关联、素材卡和发明人请求。 */
public final class PatentWorkflowDTO{private PatentWorkflowDTO(){}
 public record DisclosureRequest(@NotNull Long actorUserId,@Size(max=20000)String backgroundTechnology,@Size(max=10000)String existingProblems,
  @Size(max=10000)String technicalProblem,@NotBlank String technicalSolution,@Size(max=10000)String innovations,@Size(max=10000)String beneficialEffects,
  String embodiments,@Size(max=10000)String drawingDescription,@Size(max=10000)String keyParameters,@Size(max=10000)String alternativeSolutions,
  @Min(1)@Max(9)Integer technologyReadinessLevel,List<@NotBlank String>attachments){}
 public record DisclosureActionRequest(@NotNull Long actorUserId,@NotBlank @Pattern(regexp="SUBMIT|APPROVE|RETURN")String action,@Size(max=10000)String opinion){}
 public record StatusChangeRequest(@NotNull Long actorUserId,@NotBlank @Pattern(regexp="DISCLOSURE_DRAFT|DISCLOSURE_REVIEW|MATERIAL_PREPARATION|FILED|ACCEPTED|PRELIMINARY_EXAM|PUBLISHED|SUBSTANTIVE_EXAM|OFFICE_ACTION|RESPONSE_SUBMITTED|GRANTED|REJECTED|WITHDRAWN|INVALID|EXPIRED")String status,
  @NotNull LocalDate effectiveDate,@Size(max=10000)String remark,List<@NotBlank String>attachments,String applicationNumber,String grantNumber){}
 public record ProjectLinkRequest(@NotNull Long actorUserId,@NotNull Long researchProjectId,Long deliverableId,String deliverableType,@Size(max=10000)String description){}
 public record MaterialCardRequest(@NotNull Long actorUserId,@NotBlank @Pattern(regexp="PROBLEM|SOLUTION|INNOVATION|EXPERIMENT|DRAWING|CLAIM_FEATURE|OTHER")String category,
  @NotBlank @Size(max=255)String title,@Size(max=10000)String technicalProblemSummary,String technicalSolutionSummary,@Size(max=10000)String innovations,
  @Size(max=10000)String experimentalData,@Size(max=10000)String technicalEffects,@Size(max=10000)String drawings,@Size(max=1000)String keywords,
  @Size(max=10000)String claimFeatures,@Pattern(regexp="MANUAL|DISCLOSURE|RESEARCH_DELIVERABLE")String sourceType,Long sourceId){}
 public record MaterialCardGenerationRequest(@NotNull Long actorUserId){}
 public record InventorRequest(@NotNull Long actorUserId,Long userId,@NotBlank @Size(max=255)String name,@Size(max=255)String institution,
  @NotNull @Min(1)Integer inventorOrder,Boolean firstInventor,@Size(max=10000)String contribution,@Email String contactEmail,String contactPhone){}
 public record InventorConfirmationRequest(@NotNull Long actorUserId,@NotBlank @Pattern(regexp="CONFIRMED|REJECTED")String status){}
}
