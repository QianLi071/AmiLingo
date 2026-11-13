package com.amilingo.platform.common.dto.request;

import lombok.Data;

/**
 * 绑定/换绑手机号：第二步，提交手机号 + 验证码完成绑定。
 */
@Data
public class PhoneValidateRequest {
    private String phone;
    private String code;
}
