package com.amilingo.platform.module.research.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 里程碑下的可分配科研任务。 */
@Entity
@Table(name = "research_task")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long projectId;
    private Long milestoneId;
    @Column(nullable = false) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    private Long assigneeUserId;
    private Long createdBy;
    private LocalDate plannedStartDate;
    private LocalDate dueDate;
    private LocalDate completedDate;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private String priority;
    @Column(nullable = false) private Integer progress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() {
        if (status == null || status.isBlank()) status = "TODO";
        if (priority == null || priority.isBlank()) priority = "MEDIUM";
        if (progress == null) progress = 0;
        createdAt = updatedAt = LocalDateTime.now();
    }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
