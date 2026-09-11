package com.amilingo.platform.component.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisDistributedLock {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private static final long SLEEP_MILLIS = 50L;

    public RedisDistributedLock(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试一次加锁，立即返回
     *
     * @param key       锁 key
     * @param lockTtl   锁过期时间
     * @return 锁持有者 token，失败返回 null
     */
    public String tryLock(String key, Duration lockTtl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, token, lockTtl);
        if (Boolean.TRUE.equals(acquired)) {
            return token;
        }
        return null;
    }

    /**
     * 自旋方式等待加锁，超时则失败
     *
     * @param key       锁 key
     * @param lockTtl   锁持有时间
     * @param waitTime  等待超时时间
     * @return 锁持有者 token，失败返回 null
     */
    public String lock(String key, Duration lockTtl, Duration waitTime) {
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + waitTime.toNanos();
        while (System.nanoTime() <= deadline) {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, token, lockTtl);
            if (Boolean.TRUE.equals(acquired)) {
                return token;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(SLEEP_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        log.warn("Failed to acquire lock: {} within {}ms", key, waitTime.toMillis());
        return null;
    }

    /**
     * 使用 Lua 脚本原子地释放锁，只在 token 匹配时删除
     *
     * @param key   锁 key
     * @param token 加锁时返回的 token
     */
    public boolean unlock(String key, String token) {
        if (token == null) return false;
        Long result = redisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(key),
                token
        );
        return Long.valueOf(1L).equals(result);
    }
}