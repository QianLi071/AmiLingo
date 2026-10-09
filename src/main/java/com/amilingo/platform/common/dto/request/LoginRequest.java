package com.amilingo.platform.common.dto.request;

import lombok.Data;

import java.util.Collections;
import java.util.Map;

@Data
public class LoginRequest {
    private String loginType;
    private Map<String, Object> credential;
}
