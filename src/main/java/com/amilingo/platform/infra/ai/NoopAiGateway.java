package com.amilingo.platform.infra.ai;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AiGateway 桩实现：保证 Spring 上下文可启动，接入真实模型前所有方法均不可用。
 * 仅在 ai.provider=noop 时激活。
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "noop")
public class NoopAiGateway implements AiGateway {

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        throw new UnsupportedOperationException("AiGateway#chat 尚未接入实现");
    }

    @Override
    public <T> T chatJson(String systemPrompt, String userPrompt, Class<T> responseType) {
        throw new UnsupportedOperationException("AiGateway#chatJson 尚未接入实现");
    }

    @Override
    public String chatJsonUser(String userPrompt, double temperature, int maxTokens) {
        throw new UnsupportedOperationException("AiGateway#chatJsonUser 尚未接入实现");
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        throw new UnsupportedOperationException("AiGateway#embed 尚未接入实现");
    }

    @Override
    public double similarity(float[] a, float[] b) {
        throw new UnsupportedOperationException("AiGateway#similarity 尚未接入实现");
    }
}
