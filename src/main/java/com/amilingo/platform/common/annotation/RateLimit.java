package com.amilingo.platform.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限制同一 IP 在指定时间窗口内对 Controller 方法的访问频率。
 *
 * <p>标注于 Controller 方法上，运行时由 {@code RateLimitAspect} 拦截：
 * 以「key 前缀 + 客户端 IP」为键在 Redis 中原子自增计数，
 * 超过 {@link #maxRequests()} 时抛出 {@code 429 Too Many Requests}。</p>
 *
 * <pre>
 * &#64;RateLimit(timeWindow = 60, maxRequests = 5)
 * &#64;GetMapping("/send")
 * public ApiResponse&lt;Void&gt; send() { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /** 时间窗口（秒），默认 60 秒 */
    int timeWindow() default 60;

    /** 时间窗口内允许的最大请求次数，默认 10 次 */
    int maxRequests() default 10;

    /**
     * 限流 key 前缀。
     * 默认空串，使用「类全名#方法名」；相同前缀的方法会共享同一计数桶。
     */
    String key() default "";

    /** 触发限流时返回的提示信息 */
    String message() default "请求过于频繁，请稍后再试";
}
