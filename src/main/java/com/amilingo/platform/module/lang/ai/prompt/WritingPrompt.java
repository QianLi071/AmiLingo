package com.amilingo.platform.module.lang.ai.prompt;

/**
 * @deprecated 迭代 2 起 Prompt 单一数据源迁移至 Python 端
 * （{@code backend/app/prompts/registry.py}），Java 端不再持有 Prompt 文本。
 * 此类将在迭代 3 删除。
 */
@Deprecated
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
