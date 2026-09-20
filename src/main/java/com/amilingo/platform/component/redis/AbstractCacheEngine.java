package com.amilingo.platform.component.redis;

import com.amilingo.platform.common.exceptions.CacheException;
import com.amilingo.platform.common.exceptions.CacheMissedException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public abstract class AbstractCacheEngine<T, ID> implements ICacheable<T, ID> {
    protected final ObjectMapper objectMapper;
    protected final RedisService redisService;
    @Autowired
    public AbstractCacheEngine(ObjectMapper objectMapper, RedisService redisService) {
        this.objectMapper = objectMapper;
        this.redisService = redisService;
    }

    @Override
    public void cache(T object) throws CacheException {
        try {
            String userJson = objectMapper.writeValueAsString(object);
            String userKey = getCacheKey(object);
            redisService.setValue(userKey, userJson);
        } catch (JacksonException e) {
            log.error("Failed to cache object: {}", object, e);
            throw new CacheException(null);
        }
    }

    public String getCacheKey(T object) {
        return getKeyPrefix() + getId(object);
    }

    @Override
    public T getCachedById(@NonNull ID id) throws CacheMissedException{
        String key = getKeyPrefix() + id;
        Object json = redisService.getValue(key);
        if (json == null) {
            throw new CacheMissedException(null);
        }
        try {
            return deserializeCachedObject(json);
        } catch (JacksonException e) {
            log.error("Failed to deserialize cached object: {}", id, e);
            throw new CacheMissedException(null);
        }
    }
    public abstract T deserializeCachedObject(Object json);
    public abstract String getId(T object);

    public String getAndDeleteCacheById(String id){
        return (String) this.redisService.getRedisTemplate().opsForValue().getAndDelete(id);
    }
}
