package com.amilingo.platform.entity.user;

import com.amilingo.platform.entity.achievement.Badge;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "user_stats")
public class UserStat {
    @Id
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false, nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private Long totalExp;

    @Column(nullable = false)
    private Integer totalDays;

    @Column(nullable = false)
    private Integer currentStreak;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private List<Badge> badges;
}
