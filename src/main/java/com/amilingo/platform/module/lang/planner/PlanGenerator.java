package com.amilingo.platform.module.lang.planner;

/**
 * AI排程引擎：生成每周学习计划。
 *
 * 实现草案（依赖类型 PlanInput / PlanResult / WeeklyQuota / TimeSlot / StudyTask
 * 及 quotaCalculator、slotExtractor、taskAllocator、continuityRule、aiGateway
 * 尚未定义，待对应组件落地后实现）：
 *
 * PlanResult generate(PlanInput input)
 *   Step 1: 计算总任务量（按阶段模型+差距档位）
 *           WeeklyQuota quota = quotaCalculator.calc(input);
 *   Step 2: 从课表抽取可用时间槽（过滤<30分钟）
 *           List&lt;TimeSlot&gt; slots = slotExtractor.extract(input.getUserId(), input.getExamDate());
 *   Step 3: 贪心分配（摸底&gt;强化&gt;冲刺，薄弱优先）
 *           List&lt;StudyTask&gt; tasks = taskAllocator.allocate(quota, slots, input.getWeakSections());
 *   Step 4: 连续性规则校验（同分科不连续3天）
 *           tasks = continuityRule.adjust(tasks);
 *   Step 5: 调用AI生成文案（infra/ai/AiGateway）
 *           String weeklyGoal = aiGateway.chat(PLAN_NARRATOR_PROMPT, buildContext(tasks));
 *           return new PlanResult(tasks, weeklyGoal, warnings);
 */
public class PlanGenerator {
}
