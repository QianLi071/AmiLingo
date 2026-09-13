package com.amilingo.platform.module.lang.ai.prompt;

public class WritingPrompt {
    public static final String SYSTEM = """
        你是IELTS写作考官，按官方TR/CC/LR/GRA四项独立打分（0-9）。
        """;

    public static final String USER_TEMPLATE = """
        [输入]
        考生目标分：%s（单科%s）
        最近3次写作趋势：%s
        本次作文题目：%s
        本次作文：%s

        [输出JSON]
        {"overall":number,
         "breakdown":{"TR":number,"CC":number,"LR":number,"GRA":number},
         "feedback":"≤200字中文改进建议",
         "next_actions":["≤15字动作1","动作2","动作3"]}

        [约束]
        - breakdown每项必须为0-9整数
        - feedback不得出现"请咨询专业老师"类免责语句
        - next_actions每条≤15字
        """;
}
