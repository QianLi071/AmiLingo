package com.amilingo.platform.entity.achievement;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "badges", indexes = {@Index(name = "idx_badge_name", columnList = "name")})
public class Badge {
    @Id
    @Column(name = "badge_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Long value;
}
