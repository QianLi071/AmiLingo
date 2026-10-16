package com.amilingo.platform.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注于 Controller 方法上，表示该接口需要登录认证。
 *
 * <p>运行时由 {@code RequireAuthAspect} 拦截，在方法执行前调用
 * {@link com.amilingo.platform.common.config.security.SecurityUtil#requireAuthentication()}，
 * 未登录时抛出 {@code SecurityException}（由 {@code GlobalExceptionHandler} 统一转为 401）。</p>
 *
 * <pre>
 * &#64;RequireAuth
 * &#64;GetMapping("/send")
 * public ApiResponse&lt;Void&gt; send() { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAuth {
}
