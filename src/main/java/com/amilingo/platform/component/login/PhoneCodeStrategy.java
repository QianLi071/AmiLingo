package com.amilingo.platform.component.login;

import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.component.BaseLoginStrategy;
import com.amilingo.platform.component.services.SmsService;
import com.amilingo.platform.module.user.entity.user.User;
import com.amilingo.platform.module.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 手机号验证码登录策略（loginType = PHONE_CODE）。
 *
 * <p>验证码签发走 POST /api/v1/auth/portal/phone/send（未登录可用），
 * 校验复用统一验证码模块（{@link com.amilingo.platform.component.services.VerificationScene#PHONE_LOGIN}），
 * 与登录二次验证、邮箱/手机号绑定共用同一套生成/冷却/防爆破逻辑。</p>
 *
 * <p>验证码校验通过即视为手机号持有者本人；手机号首次登录自动注册
 * （密码为随机不可用占位哈希，用户可后续绑定邮箱、修改资料）。</p>
 */
@Service
public class PhoneCodeStrategy extends BaseLoginStrategy {

    private final SmsService smsService;
    private final UserService userService;

    public PhoneCodeStrategy(SmsService smsService, UserService userService) {
        this.smsService = smsService;
        this.userService = userService;
    }

    @Override
    public String getType() {
        return "PHONE_CODE";
    }

    /**
     * 验证码登录本身已通过短信验证手机号持有权（持有因子），
     * 不再叠加"常用设备"邮箱二次验证。
     */
    @Override
    public boolean requiresDeviceTrustVerification() {
        return false;
    }

    @Override
    protected String extractIdentityKey(Map<String, Object> params) {
        Object phone = params.get("phone");
        return phone instanceof String value ? value : null;
    }

    @Override
    protected User doAuthenticate(Map<String, Object> params) {
        Object phoneObj = params.get("phone");
        Object codeObj = params.get("code");
        if (!(phoneObj instanceof String phone) || phone.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "手机号不能为空");
        }
        if (!(codeObj instanceof String code) || code.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码不能为空");
        }
        // verify 内部完成手机号格式校验、一次性消费与防爆破
        if (!smsService.verifyLoginCode(phone, code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码错误或已过期");
        }
        return userService.findOrCreateByPhone(phone.trim());
    }
}
