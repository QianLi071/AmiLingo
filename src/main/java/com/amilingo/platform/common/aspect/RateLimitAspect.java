package com.amilingo.platform.common.aspect;

import com.amilingo.platform.common.annotation.RateLimit;
import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.component.redis.RedisService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 拦截标注了 {@link RateLimit} 的 Controller 方法，基于 Redis 实现「同一 IP 固定窗口」限流。
 *
 * <p>计数键格式：{@code rate_limit:{前缀}:{ip}}，其中前缀默认为「类全名#方法名」，
 * 也可通过 {@link RateLimit#key()} 自定义以实现多方法共享计数。</p>
 *
 * <p>当窗口内请求次数超过上限时，抛出 {@link ApiException}（HTTP 429），
 * 由 {@code GlobalExceptionHandler} 统一返回。</p>
 */
@Aspect
@Component
public class RateLimitAspect {
    private static final String KEY_PREFIX = "rate_limit:";

    private final RedisService redisService;

    public RateLimitAspect(RedisService redisService) {
        this.redisService = redisService;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String ip = resolveClientIp();
        String key = buildKey(joinPoint, rateLimit, ip);

        long count = redisService.incrementAndExpire(key, rateLimit.timeWindow());
        if (count > rateLimit.maxRequests()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, rateLimit.message());
        }
        return joinPoint.proceed();
    }

    private String buildKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit, String ip) {
        String prefix = rateLimit.key().isBlank()
                ? joinPoint.getSignature().getDeclaringTypeName() + "#" + joinPoint.getSignature().getName()
                : rateLimit.key();
        return KEY_PREFIX + prefix + ":" + ip;
    }

    /**
     * 解析客户端真实 IP，依次尝试 X-Forwarded-For、X-Real-IP，最后回退到 remoteAddr。
     */
    private String resolveClientIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }
        HttpServletRequest request = attrs.getRequest();

        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能含多个 IP，取最左侧的原始客户端 IP
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }
}
