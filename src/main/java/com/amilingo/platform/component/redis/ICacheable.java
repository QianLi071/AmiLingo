package com.amilingo.platform.component.redis;

import com.amilingo.platform.common.exceptions.CacheException;
import com.amilingo.platform.common.exceptions.CacheMissedException;
import org.springframework.stereotype.Component;

@Component
public interface ICacheable<T, ID> {
    void cache(T object) throws CacheException;
    T getCachedById(ID id) throws CacheMissedException;
    String getKeyPrefix();
}
