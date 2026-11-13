package com.amilingo.platform.component;

import com.amilingo.platform.module.user.entity.user.User;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public interface ILoginStrategy {
    String getType();
    User authenticate(Map<String, Object> params) throws IllegalArgumentException;

    /**
     * 凭证校验通过后，是否需要进行"常用位置与设备"信任检查
     * （不受信任则要求邮箱验证码二次验证，见 LoginSecurityService）。
     * 密码类登录（凭证仅为"知识因子"）应返回 true（默认）；
     * 凭证本身即完成持有权验证的方式（如短信验证码登录）可覆盖返回 false。
     */
    default boolean requiresDeviceTrustVerification() {
        return true;
    }
}
