package com.amilingo.platform.common.dto.request;

import lombok.Data;

/**
 * 登录二次验证：提交挑战 ID 与邮箱验证码，验证通过后才签发登录 Cookie。
 */
@Data
public class LoginVerifyRequest {
    private String challengeId;
    private String code;
}
