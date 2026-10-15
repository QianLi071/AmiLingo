package com.amilingo.platform.module.contest.entity;

import com.amilingo.platform.common.util.Snowflake;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** 考试场次（考位信息）。 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "exam_events")
public class ExamEvent {

    @Id
    @Column(name = "event_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(name = "exam_type", nullable = false, length = 32)
    private String examType;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "region", length = 64)
    private String region;

    @Column(name = "test_date", nullable = false)
    private LocalDate testDate;

    @Column(name = "registration_deadline")
    private LocalDate registrationDeadline;

    @Column(name = "seats_total")
    private Integer seatsTotal;

    @Column(name = "seats_left")
    private Integer seatsLeft;

    @Column(name = "status", length = 16)
    private String status;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = Snowflake.nextId();
        }
    }
}
