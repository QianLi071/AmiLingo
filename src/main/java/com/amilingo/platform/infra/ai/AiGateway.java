package com.amilingo.platform.infra.ai;


import java.util.List;

public interface AiGateway {
    //通用对话
    String chat(String systemPrompt, String userPrompt);
    //强制JSON输出
    <T> T chatJson(String systemPrompt, String userPrompt, Class<T> responseType);

    /**
     * 仅含一条 user 消息的 JSON 模式对话，返回模型原文（已去除 markdown 代码块包裹）。
     * 与原版 Python {@code ai_client.chat(messages=[{"role":"user",...}], format="json")} 对齐：
     * 写作评分的评分轮 / 逐句轮均不使用 system 消息，prompt 自身已包含全部角色设定与输出契约。
     *
     * @param userPrompt  完整 prompt（含角色、要求、JSON 契约）
     * @param temperature 采样温度（原版评分/逐句均为 0.7）
     * @param maxTokens   最大生成 token（原版两轮均为 2048）
     */
    String chatJsonUser(String userPrompt, double temperature, int maxTokens);

    //向量（用于范文检索/同义替换）
    List<float[]> embed(List<String> texts);
    //文本相似度（余弦）
    double similarity(float[] a, float[] b);
}
