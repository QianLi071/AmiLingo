package com.amilingo.platform.component.redis;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {
    @Getter
    private final RedisTemplate<String, Object> redisTemplate;
    @Autowired
    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void setValue(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void setValue(String key, String value, Duration timeout) {
        redisTemplate.opsForValue().set(key, value, timeout);
    }

    public Object getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public boolean setIfAbsent(String key, String value, Duration timeout) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, timeout));
    }

    public long getExpireSeconds(String key) {
        Long seconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return seconds == null ? -2L : seconds;
    }

    public void deleteValue(String key) {
        redisTemplate.delete(key);
    }

    public boolean deleteIfValueMatches(String key, String expectedValue) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                "if redis.call('get', KEYS[1]) == ARGV[1] then "
                        + "return redis.call('del', KEYS[1]) else return 0 end",
                Long.class
        );
        Long deleted = redisTemplate.execute(script, Collections.singletonList(key), expectedValue);
        return Long.valueOf(1L).equals(deleted);
    }

    /**
     * 原子地自增计数，并在首次自增（count == 1）时设置过期时间。
     * 用于固定窗口限流：窗口内的后续自增不会重置 TTL。
     *
     * @param key           计数键
     * @param expireSeconds 过期时间（秒），仅首次自增时生效
     * @return 自增后的计数值
     */
    public long incrementAndExpire(String key, long expireSeconds) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                "local count = redis.call('incr', KEYS[1]) "
                        + "if count == 1 then redis.call('expire', KEYS[1], ARGV[1]) end "
                        + "return count",
                Long.class
        );
        Long count = redisTemplate.execute(
                script,
                Collections.singletonList(key),
                String.valueOf(expireSeconds)
        );
        return count == null ? 0L : count;
    }
}
