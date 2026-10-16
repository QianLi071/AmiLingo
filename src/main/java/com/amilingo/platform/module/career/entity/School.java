package com.amilingo.platform.module.career.entity;

import com.amilingo.platform.common.util.Snowflake;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 院校信息。 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "schools")
public class School {

    @Id
    @Column(name = "school_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "country", length = 64)
    private String country;

    @Column(name = "city", length = 64)
    private String city;

    @Column(name = "ranking")
    private Integer ranking;

    @Column(name = "min_total_score")
    private Double minTotalScore;

    /** 最低分项分，JSON 文本。 */
    @Lob
    @Column(name = "min_section_scores")
    private String minSectionScores;

    /** 专业列表，JSON 文本。 */
    @Lob
    @Column(name = "majors")
    private String majors;

    @Column(name = "website")
    private String website;

    @Column(name = "note", length = 500)
    private String note;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = Snowflake.nextId();
        }
    }
}
