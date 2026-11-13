package com.amilingo.platform.common.config.login;

import com.amilingo.platform.component.ILoginStrategy;
import com.amilingo.platform.component.login.EmailPasswordStrategy;
import com.amilingo.platform.component.login.PhoneCodeStrategy;
import com.amilingo.platform.component.services.SmsService;
import com.amilingo.platform.module.user.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoginStrategyConfig {
    @Bean("EMAIL_PWD")
    public ILoginStrategy emailPasswordLogin(){
        return new EmailPasswordStrategy();
    }

    @Bean("PHONE_CODE")
    public ILoginStrategy phoneCodeLogin(SmsService smsService, UserService userService){
        return new PhoneCodeStrategy(smsService, userService);
    }
}
