package com.amilingo.platform.common.util;

public abstract class PetUtil {
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

    // 基于当前等级与当前等级内经验值，计算距离升级到下一级所需的剩余经验值
    // 升级所需总经验采用二次函数实现非线性增长：随等级提升所需经验递增，但增速平缓，避免升级过难
    public static long calcExpToNextLevel(long level, long currentExp){
        level = Math.max(0L, level);
        currentExp = Math.max(0L, currentExp);

        // level -> 总需求: 0: 50, 10: 250, 50: 5050, 100: 20050, 1000: 2000050
        final double BASE = 50.0;
        final double GROWTH = 2.0;
        double lvl = (double) level;
        double required = BASE + GROWTH * lvl * lvl;

        // 防止极端等级下 double 转 long 溢出
        if (required > (double) Long.MAX_VALUE) {
            required = Long.MAX_VALUE;
        }

        // 距离下一等级的剩余经验值，currentExp 已达/超过需求时返回 0
        long remaining = (long) required - currentExp;
        return Math.max(0L, remaining);
    }
}
