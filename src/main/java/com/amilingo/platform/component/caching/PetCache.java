package com.amilingo.platform.component.caching;

import com.amilingo.platform.component.redis.AbstractCacheEngine;
import com.amilingo.platform.component.redis.RedisService;
import com.amilingo.platform.module.pet.entity.pet.Pet;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class PetCache extends AbstractCacheEngine<Pet, Long> {
    public PetCache(ObjectMapper objectMapper, RedisService redisService) {
        super(objectMapper, redisService);
    }

    @Override
    public Pet deserializeCachedObject(Object json) {
        return objectMapper.readValue((String) json, Pet.class);
    }

    @Override
    public String getId(Pet object) {
        return object.getId().toString();
    }

    @Override
    public String getKeyPrefix() {
        return "pet:";
    }
}
