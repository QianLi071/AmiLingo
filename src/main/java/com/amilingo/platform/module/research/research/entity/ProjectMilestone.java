package com.amilingo.platform.module.research.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 科研项目阶段里程碑。 */
@Entity
@Table(name = "research_milestone")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectMilestone {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false) private String name;
    @Column(columnDefinition = "TEXT") private String description;
    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;
    private LocalDate completedDate;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private Integer progress;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() {
        if (status == null || status.isBlank()) status = "PENDING";
        if (progress == null) progress = 0;
        createdAt = updatedAt = LocalDateTime.now();
    }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
