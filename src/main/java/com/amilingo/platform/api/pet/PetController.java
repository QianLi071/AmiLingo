package com.amilingo.platform.api.pet;

import com.amilingo.platform.common.config.security.SecurityUtil;
import com.amilingo.platform.common.dto.ApiResponse;
import com.amilingo.platform.common.dto.response.PetStatusDTO;
import com.amilingo.platform.module.pet.entity.pet.Pet;
import com.amilingo.platform.module.pet.service.PetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/pets"})
public class PetController {

    private final PetService petService;

    public PetController(PetService petService) {
        this.petService = petService;
    }

    @GetMapping("/status")
    public ApiResponse<?> checkPetStatus() {
        Long id = SecurityUtil.getCurrentUserId();
        Pet pet = petService.getPetById(id);
        return ApiResponse.ok(Map.of(
                "code", 200,
                "data", PetStatusDTO.fromPet(pet)
        ));
    }

//    @PostMapping
//    public ApiResponse<?> createPet(@RequestBody CreatePetRequest request) {
//        petService.savePet(Pet.builder().name(request.getName()).experience(0L).level(0L).mood(Pet.Mood.HAPPY).user(null).build());
//    }
}