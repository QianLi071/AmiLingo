package com.amilingo.platform.module.research.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 加入科研项目/组队申请。 */
@Entity
@Table(name = "research_join_application")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectJoinApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false) private Long applicantUserId;
    @Column(columnDefinition = "TEXT") private String message;
    @Column(nullable = false) private String status;
    private Long reviewedBy;
    @Column(columnDefinition = "TEXT") private String reviewOpinion;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() {
        if (status == null || status.isBlank()) status = "PENDING";
        createdAt = updatedAt = LocalDateTime.now();
    }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
