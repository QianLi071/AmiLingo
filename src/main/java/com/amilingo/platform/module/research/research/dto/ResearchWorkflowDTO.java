package com.amilingo.platform.module.research.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** 科研工作流接口的请求 DTO 集合。 */
public final class ResearchWorkflowDTO {
    private ResearchWorkflowDTO() {}

    public record SubmitProjectRequest(@NotNull Long actorUserId) {}

    public record ReviewProjectRequest(
            @NotNull Long reviewerUserId,
            @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String decision,
            @Size(max = 4000) String opinion) {}

    public record JoinApplicationRequest(
            @NotNull Long applicantUserId,
            @Size(max = 2000) String message) {}

    public record ReviewJoinRequest(
            @NotNull Long reviewerUserId,
            @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String decision,
            @Size(max = 2000) String opinion,
            @Size(max = 50) String memberRole) {}

    public record MemberRequest(
            @NotNull Long actorUserId,
            @NotNull Long userId,
            @Pattern(regexp = "OWNER|MANAGER|MEMBER|ADVISOR") String role) {}

    public record UpdateMemberRequest(
            @NotNull Long actorUserId,
            @Pattern(regexp = "OWNER|MANAGER|MEMBER|ADVISOR") String role,
            @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}

    public record MilestoneRequest(
            @NotNull Long actorUserId,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 4000) String description,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            Integer sortOrder) {}

    public record UpdateMilestoneRequest(
            @NotNull Long actorUserId,
            @Size(max = 255) String name,
            @Size(max = 4000) String description,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            @Pattern(regexp = "PENDING|IN_PROGRESS|COMPLETED|BLOCKED") String status,
            @Min(0) @Max(100) Integer progress,
            Integer sortOrder) {}

    public record TaskRequest(
            @NotNull Long actorUserId,
            Long milestoneId,
            @NotBlank @Size(max = 255) String title,
            @Size(max = 4000) String description,
            Long assigneeUserId,
            LocalDate plannedStartDate,
            LocalDate dueDate,
            @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority) {}

    public record UpdateTaskRequest(
            @NotNull Long actorUserId,
            Long milestoneId,
            @Size(max = 255) String title,
            @Size(max = 4000) String description,
            Long assigneeUserId,
            LocalDate plannedStartDate,
            LocalDate dueDate,
            @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority) {}

    public record TaskProgressRequest(
            @NotNull Long actorUserId,
            @NotNull @Min(0) @Max(100) Integer progress,
            @Pattern(regexp = "TODO|IN_PROGRESS|BLOCKED|COMPLETED") String status,
            @Size(max = 4000) String comment) {}

    public record AdvisorRecordRequest(
            @NotNull Long recorderUserId,
            Long advisorUserId,
            @NotBlank @Size(max = 255) String advisorName,
            @NotNull LocalDate guidanceDate,
            @Size(max = 255) String subject,
            @NotBlank @Size(max = 10000) String content,
            @Size(max = 4000) String nextAction,
            java.util.List<@NotBlank String> attachments) {}

    public record DeliverableRequest(
            @NotNull Long creatorUserId,
            @NotBlank @Pattern(regexp = "REPORT|DATASET|SOFTWARE|PAPER|PATENT_DISCLOSURE|OTHER") String type,
            @NotBlank @Size(max = 255) String title,
            @Size(max = 10000) String description,
            @Size(max = 10000) String technicalBackground,
            @Size(max = 20000) String technicalSolution,
            @Size(max = 10000) String innovations,
            LocalDate completionDate,
            String fileName,
            String contentType,
            @PositiveOrZero Long fileSize,
            String storageKey,
            String fileUrl) {}

    public record LinkPatentRequest(
            @NotNull Long actorUserId,
            @NotNull Long patentId) {}
}
