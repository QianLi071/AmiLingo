package com.amilingo.platform.module.paper.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

/** 投稿后的编辑/审稿/返修状态记录。 */
@Entity @Table(name = "paper_review_record")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaperReviewRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long paperId;
    private Long submissionId;
    @Column(nullable = false) private Integer reviewRound;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private LocalDate statusDate;
    @Column(columnDefinition = "TEXT") private String reviewComment;
    private LocalDate revisionDeadline;
    private Long responseDraftVersionId;
    @Column(columnDefinition = "TEXT") private String attachments;
    @Column(nullable = false) private Long operatorUserId;
    private LocalDateTime createdAt;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
