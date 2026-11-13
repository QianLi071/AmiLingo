package com.amilingo.platform.module.research.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 导师指导和沟通记录。 */
@Entity
@Table(name = "research_advisor_record")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdvisorRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false) private Long recorderUserId;
    private Long advisorUserId;
    @Column(nullable = false) private String advisorName;
    @Column(nullable = false) private LocalDate guidanceDate;
    private String subject;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(columnDefinition = "TEXT") private String nextAction;
    @Column(columnDefinition = "TEXT") private String attachments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
