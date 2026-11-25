package com.amilingo.platform.common.dto.request;

import lombok.Data;

/**
 * 对进行中的登录二次验证挑战重发邮箱验证码。
 */
@Data
public class ChallengeResendRequest {
    private String challengeId;
}
