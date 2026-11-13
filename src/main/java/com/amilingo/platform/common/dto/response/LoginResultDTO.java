package com.amilingo.platform.common.dto.response;

import com.amilingo.platform.common.dto.UserDTO;
import com.amilingo.platform.module.user.entity.user.User;

/**
 * 登录接口统一返回体。
 *
 * <ul>
 *   <li>{@code verificationRequired=false}：登录成功，已写入 HttpOnly Cookie，{@code user} 为用户信息；</li>
 *   <li>{@code verificationRequired=true}：检测到陌生设备/位置，需要邮箱验证码二次验证。
 *       此时<b>不签发任何 Cookie</b>，客户端应引导用户输入邮箱验证码并携带
 *       {@code challengeId} 调用 POST /api/v1/auth/portal/login/verify 完成登录。</li>
 * </ul>
 */
public record LoginResultDTO(
        boolean verificationRequired,
        String challengeId,
        String maskedEmail,
        UserDTO user) {

    public static LoginResultDTO direct(User user) {
        return new LoginResultDTO(false, null, null, UserDTO.fromUser(user));
    }

    public static LoginResultDTO challenge(String challengeId, String maskedEmail) {
        return new LoginResultDTO(true, challengeId, maskedEmail, null);
    }
}
