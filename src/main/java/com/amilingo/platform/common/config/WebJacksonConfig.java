package com.amilingo.platform.common.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Web 层 Jackson 定制：所有 Long（含 long 原始类型）序列化为 JSON 字符串。
 *
 * <p>原因：业务主键为雪花算法 Long（如 296709300628226048），超出 JS Number
 * 的安全整数上限 2^53，前端 JSON.parse 后末位被舍入（...048 → ...050），
 * 再回传后端时找不到实体。统一输出为字符串后，前端按字符串透传/比较即可，
 * 后端反序列化 "123" → Long 同样兼容。
 *
 * <p>仅定制 Spring MVC 的 HTTP 消息转换器，不替换容器内共享 ObjectMapper，
 * Redis 缓存与 AI 网关的序列化语义保持不变。
 */
@Configuration
public class WebJacksonConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        SimpleModule longAsString = new SimpleModule();
        longAsString.addSerializer(Long.class, ToStringSerializer.instance);
        longAsString.addSerializer(Long.TYPE, ToStringSerializer.instance);
        JsonMapper mapper = JsonMapper.builder()
                .addModule(longAsString)
                .build();
        // 置于转换器列表首位，优先于默认 JSON 转换器处理 application/json
        converters.add(0, new JacksonJsonHttpMessageConverter(mapper));
    }
}
