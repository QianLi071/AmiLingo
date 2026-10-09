package com.amilingo.platform.component;

import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.common.exceptions.LoginFailedException;
import com.amilingo.platform.common.exceptions.PasswordIncorrectException;
import com.amilingo.platform.module.user.entity.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Map;

@Slf4j
public abstract class BaseLoginStrategy implements ILoginStrategy {

    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final Duration DEFAULT_LOCK_DURATION = Duration.ofMinutes(30);

    @Autowired
    protected LoginAttemptService loginAttemptService;

    @Override
    public User authenticate(Map<String, Object> params) {
        String identityKey = extractIdentityKey(params);

        // 如果需要失败次数追踪，先检查锁定状态
        if (needAttemptTracking() && identityKey != null) {
            loginAttemptService.checkLockedOrThrow(identityKey);
        }
        try {
            User user = doAuthenticate(params);
            // 认证成功，清除失败计数
            if (needAttemptTracking() && identityKey != null) {
                loginAttemptService.clearAttempts(identityKey);
            }
            return user;
        } catch (RuntimeException ex) {
            if (needAttemptTracking() && identityKey != null && isAuthFailure(ex)) {
                int remaining = (loginAttemptService.recordFailedAttempt(
                        identityKey, getMaxAttempts(), getLockDuration()));
                if (remaining == 0) {
                    throw new LoginFailedException(
                            "密码错误次数过多，账户已被锁定" + getLockDuration().toMinutes() + "分钟",
                            getLockDuration().toMinutes(),
                            true
                    );
                }
                throw new LoginFailedException("Password incorrect, please retry", remaining);
            }
            throw ex;
        }
    }

    /**
     * 子类实现具体认证逻辑；失败时抛出 RuntimeException 子类（如 ApiException / UserNotFoundException / PasswordIncorrectException）
     */
    protected abstract User doAuthenticate(Map<String, Object> params);

    /**
     * 从请求参数中提取用于计数/锁定的身份标识（手机号/邮箱/openid 等）；不需要计数时返回 null。
     */
    protected abstract String extractIdentityKey(Map<String, Object> params);

    /**
     * 是否启用失败次数追踪与自动锁定。默认启用（密码登录），子类可覆盖关闭（如验证码/微信登录）。
     */
    protected boolean needAttemptTracking() {
        return true;
    }

    /**
     * 最大失败尝试次数，默认 5。子类可覆盖。
     */
    protected int getMaxAttempts() {
        return DEFAULT_MAX_ATTEMPTS;
    }

    /**
     * 达到上限后锁定时长，默认 30 分钟。子类可覆盖。
     */
    protected Duration getLockDuration() {
        return DEFAULT_LOCK_DURATION;
    }

    /**
     * 判断异常是否属于认证失败（仅密码错误、用户不存在等才计数，非业务异常不计数）。
     * 子类可扩展覆盖。
     */
    protected boolean isAuthFailure(RuntimeException ex) {
        return ex instanceof ApiException
                || ex instanceof PasswordIncorrectException
                || ex instanceof IllegalArgumentException;
    }

    private String buildRemainingMessage(String originalMessage, int remaining) {
        if (originalMessage == null || originalMessage.isBlank()) {
            return "认证失败，还剩" + remaining + "次尝试机会";
        }
        return originalMessage + "，还剩" + remaining + "次尝试机会";
    }
}
