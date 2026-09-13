package com.amilingo.platform.common.config.login;

import com.amilingo.platform.component.ILoginStrategy;
import com.amilingo.platform.component.login.EmailPasswordStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoginStrategyConfig {
    @Bean("EMAIL_PWD")
    public ILoginStrategy emailPasswordLogin(){
        return new EmailPasswordStrategy();
    }
}
