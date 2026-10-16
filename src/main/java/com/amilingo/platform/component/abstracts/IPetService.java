package com.amilingo.platform.component.abstracts;

import com.amilingo.platform.module.pet.entity.pet.Pet;
import org.springframework.transaction.annotation.Transactional;

public interface IPetService {
    @Transactional
    void savePet(Pet user);
    Pet getPetById(Long id);
    Pet getPetByName(String name);
}
