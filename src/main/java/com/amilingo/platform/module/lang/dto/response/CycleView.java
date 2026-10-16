package com.amilingo.platform.module.lang.dto.response;

import java.util.List;
import java.util.Map;

/** 长周期规划视图：周期 + 月 + 周 + 时间切片任务 + 进度指标 + 调整建议。 */
public record CycleView(
        Cycle cycle,
        List<MonthView> months,
        List<WeekView> weeks,
        List<String> warnings,
        int taskCount,
        int doneCount,
        List<TaskView> tasks,
        Metrics metrics,
        List<AdjustmentView> adjustments) {

    public record Cycle(Long id, String examType, String startDate, String endDate,
            Double targetTotal, Double currentTotal, String status,
            String autoAdjustLevel) {
    }

    public record MonthView(String monthKey, int monthIndex, String label, String start, String end,
            String stage, String stageLabel,
            int availableMinutes, int deepMinutes, int freeMinutes, int fragmentMinutes,
            int quotaMinutes, Map<String, Integer> quotaBySection) {
    }

    public record WeekView(String monthKey, int weekIndex, String weekStart, String weekEnd,
            int availableMinutes, int quotaMinutes, int allocatedMinutes,
            Map<String, Integer> quotaBySection) {
    }

    public record TaskView(Long taskId, String section, String title, String objective,
            String start, String end, int durationMinutes,
            String stage, String taskType, boolean done,
            String monthKey, String weekStart,
            String slotType, String difficulty,
            String progressStatus, Integer actualMinutes) {
    }

    /** 进度指标（进度面板展示）。 */
    public record Metrics(int todayPlanned, int todayDone, int todayRate,
            int recentAvgRate,
            int monthDone, int monthQuota, int monthRate,
            int overdueTaskCount, int overdueMinutes) {
    }

    /** 调整建议卡片。source：AUTO 系统自动执行（可撤销）/ MANUAL 待用户处理。 */
    public record AdjustmentView(Long id, String scenario, String title, String description,
            String status, Integer triggerRate, String triggerDate,
            String source) {
    }
}
