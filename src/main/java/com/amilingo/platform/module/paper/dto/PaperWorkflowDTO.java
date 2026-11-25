package com.amilingo.platform.module.paper.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

/** 论文选题、写作、投稿、反馈和审稿工作流请求。 */
public final class PaperWorkflowDTO {
    private PaperWorkflowDTO() {}

    public record TopicRequest(@NotNull Long actorUserId, @NotBlank @Size(max=255) String title,
            @Size(max=10000) String topicBackground, @Size(max=255) String researchDirection,
            @Size(max=10000) String researchObjective, @Size(max=10000) String researchQuestion,
            @Size(max=1000) String keywords, @Size(max=255) String topicSource,
            @Size(max=255) String supervisor, LocalDate expectedCompletionDate) {}

    public record TopicStatusRequest(@NotNull Long actorUserId,
            @NotBlank @Pattern(regexp="DRAFT|CONFIRMED|ARCHIVED") String status) {}

    public record OutlineSectionRequest(@NotNull Long actorUserId, Long parentId,
            @NotBlank @Size(max=255) String title, @Size(max=10000) String summary, Integer sortOrder) {}

    public record DraftVersionRequest(@NotNull Long actorUserId,
            @NotBlank @Pattern(regexp="OUTLINE|FIRST_DRAFT|REVISION|SUBMISSION|FINAL") String versionType,
            @NotBlank String content, @Size(max=4000) String changeNote) {}

    public record RestoreVersionRequest(@NotNull Long actorUserId, @Size(max=4000) String changeNote) {}

    public record SubmissionRequest(@NotNull Long actorUserId,
            @NotBlank @Size(max=255) String venueName,
            @Pattern(regexp="JOURNAL|CONFERENCE|OTHER") String venueType,
            @Size(max=500) String submissionPlatform, @Size(max=255) String manuscriptNumber,
            @Size(max=255) String correspondingAuthor, @Size(max=10000) String authorSnapshot,
            Long draftVersionId, @NotNull LocalDate submissionDate,
            @Size(max=10000) String notes, List<@NotBlank String> attachments) {}

    public record SubmissionStatusRequest(@NotNull Long actorUserId,
            @NotBlank @Pattern(regexp="SUBMITTED|UNDER_REVIEW|REVISION|ACCEPTED|REJECTED|WITHDRAWN") String status,
            @Size(max=4000) String notes) {}

    public record FeedbackRequest(@NotNull Long actorUserId, Long draftVersionId, Long feedbackUserId,
            @NotBlank @Size(max=255) String feedbackAuthor, @NotNull LocalDate feedbackDate,
            @Size(max=255) String subject, @NotBlank @Size(max=20000) String content,
            @Size(max=10000) String modificationRequirement, LocalDate dueDate,
            List<@NotBlank String> attachments) {}

    public record FeedbackProgressRequest(@NotNull Long actorUserId,
            @NotBlank @Pattern(regexp="PENDING|IN_PROGRESS|RESOLVED|REJECTED") String status,
            @Size(max=10000) String studentReply, Long resolvedVersionId) {}

    public record ReviewRecordRequest(@NotNull Long actorUserId, Long submissionId,
            @NotNull @Min(1) Integer reviewRound,
            @NotBlank @Pattern(regexp="EDITOR_CHECK|UNDER_REVIEW|MINOR_REVISION|MAJOR_REVISION|REVISION_SUBMITTED|ACCEPTED|REJECTED|PROOF|ONLINE|PUBLISHED") String status,
            @NotNull LocalDate statusDate, @Size(max=20000) String reviewComment,
            LocalDate revisionDeadline, Long responseDraftVersionId, List<@NotBlank String> attachments) {}
}
