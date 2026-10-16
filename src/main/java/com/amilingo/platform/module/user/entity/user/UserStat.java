package com.amilingo.platform.module.user.entity.user;

import com.amilingo.platform.module.user.entity.achievement.Badge;
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
    @Column(name = "user_id", updatable = false, nullable = false, unique = true)
    private Long user;

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
