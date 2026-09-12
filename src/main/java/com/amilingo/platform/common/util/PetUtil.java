package com.amilingo.platform.common.util;

public class PetUtil {
    public static long calcExp(long score, long duration){
        // 限制输入范围，避免异常值
        score = Math.max(0, Math.min(100, score));
        duration = Math.max(0, duration);

        // 基础经验：每分score对应10点经验，满分100对应1000点
        long baseExp = score * 10L;

        // 时长加成：使用平方根实现递减收益，避免发散
        // duration=0 -> 1.0x, duration=60 -> ~1.41x, duration=1440 -> 5.0x
        double durationMultiplier = Math.sqrt(duration / 60.0 + 1.0);

        long exp = (long) (baseExp * durationMultiplier);

        // 硬性上限，防止溢出并保持数值合理
        final long MAX_EXP = 10000L;
        return Math.min(exp, MAX_EXP);
    }
}
