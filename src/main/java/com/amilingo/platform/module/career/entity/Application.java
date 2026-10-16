package com.amilingo.platform.module.career.entity;

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

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 院校申请记录（状态机：DRAFT/IN_PROGRESS/SUBMITTED/OFFER/REJECTED）。 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "applications", indexes = {
        @Index(name = "idx_app_user", columnList = "user_id")
})
public class Application {

    @Id
    @Column(name = "application_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "program", length = 128)
    private String program;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(name = "note", length = 500)
    private String note;

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
