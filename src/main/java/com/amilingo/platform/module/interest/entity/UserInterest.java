package com.amilingo.platform.module.interest.entity;

import com.amilingo.platform.common.util.Snowflake;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 用户兴趣（用户与标签的关联，含兴趣程度 0-100）。 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_interests", indexes = {
        @Index(name = "idx_ui_user", columnList = "user_id"),
        @Index(name = "idx_ui_tag", columnList = "tag_id")
})
public class UserInterest {

    @Id
    @Column(name = "user_interest_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(name = "level")
    private Integer level;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = Snowflake.nextId();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
