package com.amilingo.platform.component.caching;

import com.amilingo.platform.common.exceptions.CacheMissedException;
import com.amilingo.platform.component.redis.AbstractCacheEngine;
import com.amilingo.platform.component.redis.RedisService;
import com.amilingo.platform.module.user.entity.user.User;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class UserCache extends AbstractCacheEngine<User, Long> {

    private String emailPrefix = "email";

    public UserCache(ObjectMapper objectMapper, RedisService redisService) {
        super(objectMapper, redisService);
        emailPrefix = getKeyPrefix() + emailPrefix;
    }

    @Override
    public User deserializeCachedObject(Object json) {
        User user = objectMapper.readValue((String) json, User.class);
        log.info("Deserialized as {}", user);
        return user;
    }

    public User getUserByEmail(@NonNull String email){
        String phoneKey = emailPrefix + email;
        Object userIdStr = redisService.getValue(phoneKey);

        if (userIdStr == null) {
            throw new CacheMissedException(null);
        }
        try {
            return getCachedById(Long.parseLong((String) userIdStr));
        } catch (NumberFormatException e) {
            redisService.deleteValue(phoneKey);
            throw new CacheMissedException(e.getMessage());
        }
    }

    public void setUserEmailKey(User user){
        redisService.setValue(emailPrefix + user.getEmail(), String.valueOf(user.getId()));
    }

    @Override
    public String getId(User user) {
        return user.getId().toString();
    }

    @Override
    public String getKeyPrefix() {
        return "user";
    }
}
