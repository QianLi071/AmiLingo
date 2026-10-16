package com.amilingo.platform.module.interest.entity;

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

/** 兴趣标签。 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "interest_tags")
public class InterestTag {

    @Id
    @Column(name = "tag_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "category", length = 64)
    private String category;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = Snowflake.nextId();
        }
    }
}
