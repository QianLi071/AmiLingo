package com.amilingo.platform.common.dto.request;

import lombok.Data;

/**
 * 发送手机号登录验证码（未登录链路）。
 */
@Data
public class PhoneCodeSendRequest {
    private String phone;
}
