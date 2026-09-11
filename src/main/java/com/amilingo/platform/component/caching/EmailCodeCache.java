package com.amilingo.platform.component.caching;

import com.amilingo.platform.component.redis.AbstractCacheEngine;
import com.amilingo.platform.component.redis.RedisService;
import lombok.NonNull;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class EmailCodeCache extends AbstractCacheEngine<String, String> {
    public EmailCodeCache(ObjectMapper objectMapper, RedisService redisService) {
        super(objectMapper, redisService);
    }

    @Override
    public String deserializeCachedObject(@NonNull Object json) {
        return json.toString();
    }

    @Override
    public String getId(@NonNull String object) {
        return object;
    }

    @Override
    public String getKeyPrefix() {
        return "email";
    }
}
