package com.amilingo.platform.component.services;

import com.amilingo.platform.common.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 开发环境短信桩实现：验证码仅输出到服务端日志（app.sms.provider=log 时生效，默认）。
 *
 * <p>⚠️ 严禁用于生产：日志可能被采集/泄露，导致验证码泄露。
 * 生产环境请接入真实短信供应商（新增 {@link SmsSender} 实现），
 * 并将 {@code app.sms.provider} 切换为对应值，使本桩自动退让。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "log", matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {

    @Override
    public void send(String phone, String code, String purpose) {
        log.warn("[DEV-ONLY] 短信验证码 -> 手机号:{} 用途:{} 验证码:{}",
                StringUtil.maskPhone(phone), purpose, code);
    }
}
