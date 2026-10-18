package com.amilingo.platform.module.career.entity;

import com.amilingo.platform.common.util.Snowflake;
import com.amilingo.platform.module.career.enums.JobRegion;
import com.amilingo.platform.module.career.enums.JobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 来自企业官网或公开招聘渠道的岗位信息。 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "job_postings", indexes = {
        @Index(name = "idx_job_region_type", columnList = "region,job_type"),
        @Index(name = "idx_job_deadline", columnList = "application_deadline")
})
public class JobPosting {

    @Id
    @Column(name = "job_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(name = "company_name", nullable = false, length = 128)
    private String companyName;

    @Column(name = "position_title", nullable = false, length = 128)
    private String positionTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "region", nullable = false, length = 32)
    private JobRegion region;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 32)
    private JobType jobType;

    @Column(name = "industry", length = 64)
    private String industry;

    @Column(name = "location", length = 128)
    private String location;

    @Lob
    @Column(name = "description", nullable = false)
    private String description;

    @Lob
    @Column(name = "requirements")
    private String requirements;

    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;

    @Column(name = "official_url", nullable = false, length = 1000)
    private String officialUrl;

    @Column(name = "source_name", nullable = false, length = 128)
    private String sourceName;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = Snowflake.nextId();
        }
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
