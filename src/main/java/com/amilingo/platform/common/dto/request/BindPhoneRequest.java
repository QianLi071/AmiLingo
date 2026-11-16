package com.amilingo.platform.common.dto.request;

import lombok.Data;

/**
 * 绑定/换绑手机号：第一步，向新手机号发送验证码。
 */
@Data
public class BindPhoneRequest {
    private String phone;
}
