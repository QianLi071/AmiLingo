package com.amilingo.platform.module.research.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 任务进度变更审计记录。 */
@Entity
@Table(name = "research_task_progress_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskProgressLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long taskId;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false) private Long operatorUserId;
    private Integer previousProgress;
    @Column(nullable = false) private Integer currentProgress;
    private String previousStatus;
    @Column(nullable = false) private String currentStatus;
    @Column(columnDefinition = "TEXT") private String comment;
    private LocalDateTime createdAt;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
