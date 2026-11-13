package com.amilingo.platform.component.services;

/**
 * 短信发送 SPI。
 *
 * <p>生产环境必须提供真实供应商实现（阿里云/腾讯云短信等），并通过
 * {@code app.sms.provider} 配置注册；业务代码只依赖本接口，
 * 禁止在任何业务模块直接依赖具体短信供应商 SDK。</p>
 */
public interface SmsSender {

    /**
     * 发送验证码短信。
     *
     * @param phone   已通过格式校验的手机号
     * @param code    明文验证码（仅在此处使用，禁止落日志以外的持久化）
     * @param purpose 用途描述（如"手机号登录"）
     */
    void send(String phone, String code, String purpose);
}
