package com.amilingo.platform.infra.ai;


import java.util.List;

public interface AiGateway {
    //通用对话
    String chat(String systemPrompt, String userPrompt);
    //强制JSON输出
    <T> T chatJson(String systemPrompt, String userPrompt, Class<T> responseType);
    //向量（用于范文检索/同义替换）
    List<float[]> embed(List<String> texts);
    //文本相似度（余弦）
    double similarity(float[] a, float[] b);
}
