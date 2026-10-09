package com.amilingo.platform.common.dto.response;

import com.amilingo.platform.common.util.PetUtil;
import com.amilingo.platform.module.pet.entity.pet.Pet;
import lombok.*;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PetStatusDTO {
    private String name;
    private Long level;
    private Long exp;
    private Long expToNext;
    private Pet.Stage evolution;
    private Pet.Mood mood;

    public static PetStatusDTO fromPet(Pet pet) {
        return PetStatusDTO
                .builder()
                .name(pet.getName())
                .level(pet.getLevel())
                .exp(pet.getExperience())
                .expToNext(PetUtil.calcExpToNextLevel(pet.getLevel(), pet.getExperience()))
                .mood(pet.getMood())
                .evolution(pet.getStage())
                .build();
    }
}
