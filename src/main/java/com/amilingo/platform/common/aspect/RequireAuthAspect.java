package com.amilingo.platform.common.aspect;

import com.amilingo.platform.common.annotation.RequireAuth;
import com.amilingo.platform.common.config.security.SecurityUtil;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * 拦截标注了 {@link RequireAuth} 的 Controller 方法，在方法执行前检查登录状态。
 *
 * <p>未登录时 {@link SecurityUtil#requireAuthentication()} 抛出 {@code SecurityException}，
 * 由 {@code GlobalExceptionHandler} 统一返回 401。</p>
 */
@Aspect
@Component
public class RequireAuthAspect {

    @Before("@annotation(requireAuth)")
    public void before(RequireAuth requireAuth) {
        SecurityUtil.requireAuthentication();
    }
}
