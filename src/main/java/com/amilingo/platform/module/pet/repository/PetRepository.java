package com.amilingo.platform.module.pet.repository;

import com.amilingo.platform.module.pet.entity.pet.Pet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PetRepository extends JpaRepository<Pet,Long> {
    Optional<Pet> findPetByName(String name);
}
