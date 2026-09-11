package com.amilingo.platform.component;

import com.amilingo.platform.entity.user.User;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public interface ILoginStrategy {
    String getType();
    User authenticate(Map<String, Object> params) throws IllegalArgumentException;
}
