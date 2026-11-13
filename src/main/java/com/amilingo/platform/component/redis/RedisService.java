package com.amilingo.platform.component.redis;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
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

    /**
     * 向列表尾部追加元素，并原子地裁剪为最近 maxSize 个、刷新过期时间。
     * 用于"最近 N 个受信任登录指纹"这类有界 FIFO 场景，防止列表无限膨胀。
     */
    public void appendToListTail(String key, String value, int maxSize, Duration ttl) {
        int effectiveMaxSize = Math.max(1, maxSize);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                "redis.call('rpush', KEYS[1], ARGV[1]) "
                        + "redis.call('ltrim', KEYS[1], ARGV[2], '-1') "
                        + "redis.call('expire', KEYS[1], ARGV[3]) "
                        + "return 1",
                Long.class
        );
        redisTemplate.execute(
                script,
                Collections.singletonList(key),
                value,
                String.valueOf(-effectiveMaxSize),
                String.valueOf(ttl.toSeconds())
        );
    }

    /**
     * 读取整个列表。key 不存在时返回空列表；Redis 异常向上抛出（由调用方决定降级策略）。
     */
    public List<Object> getList(String key) {
        List<Object> values = redisTemplate.opsForList().range(key, 0, -1);
        return values == null ? List.of() : values;
    }

    /**
     * 刷新 key 的过期时间。key 不存在时无效果。
     */
    public void expire(String key, Duration ttl) {
        redisTemplate.expire(key, ttl);
    }
}
