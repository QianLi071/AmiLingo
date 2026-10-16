package com.amilingo.platform.module.lang.entity;

import com.amilingo.platform.common.util.Snowflake;
import com.amilingo.platform.module.lang.enums.Section;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 单科目标分（隶属于一个 ExamGoal）。 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "score_targets", indexes = {
        @Index(name = "idx_target_user", columnList = "user_id"),
        @Index(name = "idx_target_goal", columnList = "goal_id")
})
public class ScoreTarget {

    @Id
    @Column(name = "target_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "goal_id")
    private Long goalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 16)
    private Section section;

    @Column(name = "target_score", nullable = false)
    private Double targetScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = Snowflake.nextId();
        }
    }
}
