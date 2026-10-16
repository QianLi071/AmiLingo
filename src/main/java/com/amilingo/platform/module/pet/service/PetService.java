package com.amilingo.platform.module.pet.service;

import com.amilingo.platform.common.exceptions.CacheException;
import com.amilingo.platform.common.exceptions.CacheMissedException;
import com.amilingo.platform.component.abstracts.IPetService;
import com.amilingo.platform.component.caching.PetCache;
import com.amilingo.platform.module.pet.entity.pet.Pet;
import com.amilingo.platform.module.pet.repository.PetRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PetService implements IPetService {
    private final PetCache petCache;
    private final PetRepository petRepository;

    public PetService(PetCache petCache, PetRepository petRepository) {
        this.petCache = petCache;
        this.petRepository = petRepository;
    }

    @Override
    public void savePet(Pet pet) {
        try {
            petCache.cache(pet);
        } catch (CacheException e){
            petRepository.save(pet);
        }
    }

    @Override
    public Pet getPetById(Long id) {
        try {
            return petCache.getCachedById(id);
        } catch (CacheMissedException e) {
            Optional<Pet> userOpt = petRepository.findById(id);
            if (userOpt.isPresent()) {
                Pet pet = userOpt.get();
                petCache.cache(pet);
                return pet;
            }
        }
        return null;
    }

    @Override
    public Pet getPetByName(String name) {
        return null;
    }

    /**
     * 尽力为用户宠物增加经验：宠物存在才处理，不存在/出错均静默跳过（不影响主业务链路）。
     * 依据新经验重新计算等级。
     */
    public void grantExperience(Long userId, long exp) {
        try {
            Pet pet = getPetById(userId);
            if (pet == null || exp <= 0) {
                return;
            }
            long newExp = pet.getExperience() + exp;
            pet.setExperience(newExp);
            // 每 500 经验升 1 级
            pet.setLevel(newExp / 500);
            petRepository.save(pet);
        } catch (Exception e) {
            // 降级：忽略宠物经验异常
        }
    }
}
