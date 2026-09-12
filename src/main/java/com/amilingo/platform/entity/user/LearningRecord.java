package com.amilingo.platform.entity.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "learning_records", indexes = {@Index(name = "idx_lr_user_id", columnList = "user_id"),
        @Index(name = "idx_lr_taskname", columnList = "task_name")})
public class LearningRecord {
    @Id
    @Column(name = "record_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    @Column(nullable = false)
    private Zone zone;

    @Column(nullable = false)
    private String taskName;

    @Column(nullable = false)
    private Byte score;

    @Column(nullable = false)
    private Integer durationMinutes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum Zone {
        LANG, CONTEXT, CAREER, INTEREST
    }
}
