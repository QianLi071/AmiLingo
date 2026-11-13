package com.amilingo.platform.component.services;

/**
 * 验证码业务场景。
 *
 * <p>同一渠道下不同场景的验证码在 Redis 中相互隔离（key 含场景名），
 * 严格防止跨场景重放——例如"登录二次验证"的验证码不能用于"绑定邮箱"，
 * "手机号登录"的验证码不能用于"绑定手机号"。</p>
 */
public enum VerificationScene {
    /** 邮箱密码登录时，陌生设备/位置的二次验证（发给账号本人邮箱） */
    EMAIL_LOGIN_STEP_UP(VerificationChannel.EMAIL, "登录安全验证"),
    /** 绑定/换绑邮箱（发给待绑定的新邮箱） */
    EMAIL_BIND(VerificationChannel.EMAIL, "邮箱绑定"),
    /** 手机号验证码登录 */
    PHONE_LOGIN(VerificationChannel.SMS, "手机号登录"),
    /** 绑定/换绑手机号 */
    PHONE_BIND(VerificationChannel.SMS, "手机号绑定");

    private final VerificationChannel channel;
    private final String description;

    VerificationScene(VerificationChannel channel, String description) {
        this.channel = channel;
        this.description = description;
    }

    public VerificationChannel getChannel() {
        return channel;
    }

    /** 人可读用途描述（用于邮件主题/短信文案），禁止用于拼接 Redis key 之外的逻辑 */
    public String getDescription() {
        return description;
    }
}
