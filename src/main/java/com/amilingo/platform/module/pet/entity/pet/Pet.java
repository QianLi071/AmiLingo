package com.amilingo.platform.module.pet.entity.pet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
    @Column(name = "pet_user_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(nullable = false, unique = true)
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
