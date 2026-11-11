package com.amilingo.platform.module.paper.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

/** 一次投稿或转投记录。 */
@Entity @Table(name = "paper_submission")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaperSubmission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long paperId;
    @Column(nullable = false) private Integer roundNo;
    @Column(nullable = false) private String venueName;
    private String venueType;
    private String submissionPlatform;
    private String manuscriptNumber;
    private String correspondingAuthor;
    @Column(columnDefinition = "TEXT") private String authorSnapshot;
    private Long draftVersionId;
    @Column(nullable = false) private LocalDate submissionDate;
    @Column(nullable = false) private String status;
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(columnDefinition = "TEXT") private String attachments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist void onCreate() { if (status == null) status = "SUBMITTED"; createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
