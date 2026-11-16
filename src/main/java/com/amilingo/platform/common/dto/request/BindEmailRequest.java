package com.amilingo.platform.common.dto.request;

import lombok.Data;

/**
 * 绑定/换绑邮箱：第一步，向新邮箱发送验证码。
 */
@Data
public class BindEmailRequest {
    private String email;
}
