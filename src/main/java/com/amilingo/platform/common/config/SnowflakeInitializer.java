package com.amilingo.platform.common.config;

import com.amilingo.platform.common.util.Snowflake;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 启动时将配置的 workerId / datacenterId 注入 {@link Snowflake}。
 * static 字段无法用 {@code @Value} 直接注入，故在此显式调用。
 */
@Component
public class SnowflakeInitializer {

    @Value("${microservice.workerid:0}")
    private long workerId;

    @Value("${microservice.dadacenterid:0}")
    private long datacenterId;

    @PostConstruct
    public void init() {
        Snowflake.configure(workerId, datacenterId);
    }
}
