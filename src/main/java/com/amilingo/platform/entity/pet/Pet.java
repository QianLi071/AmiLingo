package com.amilingo.platform.entity.pet;

import com.amilingo.platform.entity.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "pets")
public class Pet {
    @Id
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false, nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(name = "exp", nullable = false)
    private Long experience;

    @Column(nullable = false)
    private Long level;

    @Column(name = "evolution_stage", nullable = false)
    private Stage stage;

    private Mood mood = Mood.HAPPY;

    @Column(nullable = false, updatable = false)
    private LocalDateTime lastFedAt;

    public enum Stage {
        EGG, BABY, ADULT, LEGEND
    }
    public enum Mood {
        HAPPY, SAD, NEUTRAL
    }
}
