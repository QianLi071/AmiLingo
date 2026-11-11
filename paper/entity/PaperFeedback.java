package com.amilingo.platform.module.paper.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

/** 导师反馈、学生回复及处理状态。 */
@Entity @Table(name = "paper_feedback")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaperFeedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long paperId;
    private Long draftVersionId;
    private Long feedbackUserId;
    @Column(nullable = false) private String feedbackAuthor;
    @Column(nullable = false) private LocalDate feedbackDate;
    private String subject;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(columnDefinition = "TEXT") private String modificationRequirement;
    private LocalDate dueDate;
    @Column(nullable = false) private String status;
    @Column(columnDefinition = "TEXT") private String studentReply;
    private Long resolvedVersionId;
    @Column(columnDefinition = "TEXT") private String attachments;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist void onCreate() { if (status == null) status = "PENDING"; createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
