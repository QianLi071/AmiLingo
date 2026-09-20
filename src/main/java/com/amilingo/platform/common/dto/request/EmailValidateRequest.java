package com.amilingo.platform.common.dto.request;

import lombok.Data;

@Data
public class EmailValidateRequest {
    private String email;
    private String code;
}
