package com.amilingo.platform.component.login;

import com.amilingo.platform.common.exceptions.ApiException;
import com.amilingo.platform.component.BaseLoginStrategy;
import com.amilingo.platform.module.user.entity.user.User;
import com.amilingo.platform.module.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailPasswordStrategy extends BaseLoginStrategy {

    @Autowired
    private UserService userService;

    @Override
    public String getType() {
        return "EMAIL_PWD";
    }

    @Override
    protected String extractIdentityKey(Map<String, Object> params) {
        return String.valueOf(params.get("email"));
    }

    @Override
    protected User doAuthenticate(Map<String, Object> params) {
        String email = (String) params.get("email");
        String password = (String) params.get("password");

        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "邮箱不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
        User user = userService.loginViaEmailPwd(email, password);

        if (user == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "邮箱或密码错误");
        }
        return user;
    }
}
