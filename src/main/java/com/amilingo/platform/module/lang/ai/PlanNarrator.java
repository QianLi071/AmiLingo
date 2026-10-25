package com.amilingo.platform.module.lang.ai;

import com.amilingo.platform.infra.ai.AiGateway;
import com.amilingo.platform.module.lang.enums.Section;
import com.amilingo.platform.module.lang.planner.PlanInput;
import com.amilingo.platform.module.lang.planner.StageModel;
import com.amilingo.platform.module.lang.planner.StudyTask;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 排程文案器（配合 PlanGenerator Step 5）：根据本周任务安排，
 * 通过 {@link AiGateway} 生成一段简明的本周目标 / 激励文案。
 */
@Component
public class PlanNarrator {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private static final String SYSTEM = """
            你是贴心的学习教练，请根据给定的本周学习安排，写一段 80–140 字的中文「本周目标」：
            说明本周所处阶段、学习重点（尤其薄弱分科）、任务节奏与一句鼓励。
            语言简洁积极、可执行，不要分点罗列，不要出现免责套话。
            """;

    private final AiGateway aiGateway;

    public PlanNarrator(AiGateway aiGateway) {
        this.aiGateway = aiGateway;
    }

    public String narrate(PlanInput input, StageModel.Stage stage, List<StudyTask> allTasks) {
        List<StudyTask> currentWeek = allTasks.stream()
                .filter(t -> !t.getScheduledStart().toLocalDate().isAfter(input.planStart().plusDays(6)))
                .filter(t -> !t.getScheduledStart().toLocalDate().isBefore(input.planStart()))
                .toList();

        Map<Section, Long> counts = currentWeek.stream()
                .collect(Collectors.groupingBy(StudyTask::getSection, Collectors.counting()));
        int totalMinutes = currentWeek.stream().mapToInt(StudyTask::getDurationMinutes).sum();

        StringBuilder schedule = new StringBuilder();
        for (StudyTask task : currentWeek.stream().limit(14).toList()) {
            schedule.append("- ").append(task.getScheduledStart().format(TIME))
                    .append(" ").append(task.getSection().getCode())
                    .append("·").append(task.getTitle()).append('\n');
        }

        String userPrompt = """
                本周阶段：%s
                考试类型：%s，目标总分：%s
                本周分科任务数：%s，总时长约 %d 分钟
                薄弱分科：%s
                本周任务安排：
                %s
                """.formatted(
                stage.getDisplayName(),
                input.examType().name(),
                input.targetTotal() != null ? input.targetTotal() : "未提供",
                counts,
                totalMinutes,
                input.weakSections() != null && !input.weakSections().isEmpty()
                        ? input.weakSections().get(0).getDisplayName() : "无",
                schedule.length() == 0 ? "（本周无安排）" : schedule.toString());

        String text = aiGateway.chat(SYSTEM, userPrompt);
        return text != null ? text.trim() : "";
    }
}
