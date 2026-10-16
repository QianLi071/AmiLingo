package com.amilingo.platform.module.lang.planner;

import com.amilingo.platform.module.lang.enums.Section;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 进度驱动的规划调整器（对应第三节：进度自动同步 & 动态调整）。
 * 每次任务进度回调后由 Service 触发 evaluate，产出 PENDING 状态的 PlanAdjustment 建议，
 * 由用户在前端确认后真正生效。
 *
 * 三个场景：
 * 场景1 少量未完成 → 迁移到最近空闲切片（不改配额）
 * 场景2 连续低完成率 → 预警+顺延+询问下调配额
 * 场景3 连续超额 → 增加本周任务量
 * 防堆积：延期任务超阈值 / 单日高强度超限 → 预警
 */
public class ProgressAdjuster {

    /* 判定阈值（可按需调整） */
    private static final int LOOKBACK_DAYS = 3;                  // 近 N 天窗口
    private static final int LOW_RATE_THRESHOLD = 50;            // 单日完成率 < 50% 视为低
    private static final int HIGH_RATE_THRESHOLD = 120;          // 单日完成率 > 120% 视为超额（actual > planned*1.2）
    private static final int FEW_UNDONE_MAX = 3;                 // 场景1：未完成任务数 ≤ 3 视为少量
    private static final int PILE_UP_TASK_THRESHOLD = 8;         // 防堆积：延期任务 > 8 个触发预警
    private static final int PILE_UP_MINUTE_THRESHOLD = 240;     // 防堆积：延期总分钟 > 240 触发预警
    private static final int HIGH_INTENSITY_DAILY_CAP = 240;     // 单日高强度任务上限 240 分钟

    /** 进度指标汇总（用于前端进度面板 + 场景判断）。 */
    public record Metrics(
            int todayPlanned,        // 当日计划分钟数
            int todayDone,           // 当日已完成分钟数
            int todayRate,           // 当日完成率 0-200（超额时 > 100）
            int recentAvgRate,       // 近 N 天平均完成率
            int monthDone,           // 当月累计已完成分钟数
            int monthQuota,          // 当月配额分钟数
            int monthRate,           // 当月完成率
            int overdueTaskCount,    // 已过期仍未完成的任务数
            int overdueMinutes,      // 已过期任务总分钟数
            int todayHighMinutes,    // 当日高强度任务分钟数
            List<StudyTask> overdueTasks   // 过期任务明细
    ) {
    }

    /** 场景判断结果。 */
    public record Decision(
            String scenario,         // SCENARIO1_MIGRATE / SCENARIO2_REDUCE / SCENARIO3_ACCELERATE / PILE_UP_WARNING / NONE
            String title,
            String description,
            String payloadJson,
            int triggerRate
    ) {
    }

    /**
     * 计算进度指标。
     *
     * @param tasks     当前周期下全部任务
     * @param monthQuota 当月配额（分钟）
     * @param today     今日
     */
    public static Metrics computeMetrics(List<StudyTask> tasks, int monthQuota, LocalDate today) {
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        int todayPlanned = 0;
        int todayDone = 0;
        int todayHigh = 0;
        int monthDone = 0;
        List<StudyTask> overdue = new ArrayList<>();
        int overdueMinutes = 0;

        for (StudyTask t : tasks) {
            LocalDateTime start = t.getScheduledStart();
            if (start == null) continue;
            // 当日
            if (!start.isBefore(todayStart) && start.isBefore(tomorrowStart)) {
                int dur = t.getDurationMinutes() == null ? 0 : t.getDurationMinutes();
                todayPlanned += dur;
                if ("DONE".equals(t.getProgressStatus())) {
                    todayDone += (t.getActualMinutes() != null ? t.getActualMinutes() : dur);
                } else if ("PARTIAL".equals(t.getProgressStatus()) && t.getActualMinutes() != null) {
                    todayDone += t.getActualMinutes();
                }
                if ("HIGH".equals(t.getDifficulty())) {
                    todayHigh += dur;
                }
            }
            // 当月累计
            if ("DONE".equals(t.getProgressStatus())) {
                monthDone += (t.getActualMinutes() != null ? t.getActualMinutes()
                        : (t.getDurationMinutes() == null ? 0 : t.getDurationMinutes()));
            } else if ("PARTIAL".equals(t.getProgressStatus()) && t.getActualMinutes() != null) {
                monthDone += t.getActualMinutes();
            }
            // 过期未完成
            if (start.isBefore(todayStart) && !"DONE".equals(t.getProgressStatus())) {
                overdue.add(t);
                overdueMinutes += (t.getDurationMinutes() == null ? 0 : t.getDurationMinutes());
            }
        }

        int todayRate = todayPlanned == 0 ? 100 : Math.round(todayDone * 100f / todayPlanned);
        int monthRate = monthQuota == 0 ? 0 : Math.round(monthDone * 100f / monthQuota);

        // 近 N 天平均完成率
        int recentSum = 0;
        int recentDays = 0;
        for (int i = 1; i <= LOOKBACK_DAYS; i++) {
            LocalDate d = today.minusDays(i);
            LocalDateTime ds = d.atStartOfDay();
            LocalDateTime de = d.plusDays(1).atStartOfDay();
            int planned = 0;
            int done = 0;
            for (StudyTask t : tasks) {
                LocalDateTime start = t.getScheduledStart();
                if (start == null || start.isBefore(ds) || !start.isBefore(de)) continue;
                int dur = t.getDurationMinutes() == null ? 0 : t.getDurationMinutes();
                planned += dur;
                if ("DONE".equals(t.getProgressStatus())) {
                    done += (t.getActualMinutes() != null ? t.getActualMinutes() : dur);
                } else if ("PARTIAL".equals(t.getProgressStatus()) && t.getActualMinutes() != null) {
                    done += t.getActualMinutes();
                }
            }
            if (planned > 0) {
                recentSum += Math.round(done * 100f / planned);
                recentDays++;
            }
        }
        int recentAvg = recentDays == 0 ? todayRate : recentSum / recentDays;

        return new Metrics(todayPlanned, todayDone, todayRate, recentAvg,
                monthDone, monthQuota, monthRate,
                overdue.size(), overdueMinutes, todayHigh, overdue);
    }

    /**
     * 根据指标判断应给出哪类建议。
     * 优先级：防堆积 > 场景2 > 场景1 > 场景3。
     */
    public static Decision decide(Metrics m, Long cycleId, LocalDate today,
                                  Map<Section, Integer> monthQuotaBySection) {
        // 防堆积预警（最高优先级）
        if (m.overdueTaskCount() > PILE_UP_TASK_THRESHOLD
                || m.overdueMinutes() > PILE_UP_MINUTE_THRESHOLD) {
            String payload = "{\"overdueTaskCount\":" + m.overdueTaskCount()
                    + ",\"overdueMinutes\":" + m.overdueMinutes() + "}";
            return new Decision("PILE_UP_WARNING",
                    "延期任务堆积预警",
                    "当前有 " + m.overdueTaskCount() + " 个任务逾期未做（共约 "
                            + m.overdueMinutes() + " 分钟）。已接近月度截止红线，建议尽快清理或考虑下调后续配额。",
                    payload, m.todayRate());
        }
        // 单日高强度上限保护
        if (m.todayHighMinutes() > HIGH_INTENSITY_DAILY_CAP) {
            String payload = "{\"todayHighMinutes\":" + m.todayHighMinutes() + "}";
            return new Decision("PILE_UP_WARNING",
                    "单日高强度任务过载",
                    "今天安排了 " + m.todayHighMinutes() + " 分钟高强度任务，超过单日上限 "
                            + HIGH_INTENSITY_DAILY_CAP + " 分钟，容易导致疲劳。建议将部分任务迁移到后续日期。",
                    payload, m.todayRate());
        }
        // 场景2：连续多日低完成率
        if (m.recentAvgRate() < LOW_RATE_THRESHOLD && m.monthRate() < LOW_RATE_THRESHOLD) {
            // 建议：把本周剩余未完成任务顺延到下周，并询问是否下调当月配额
            int suggestReduce = Math.round(m.monthQuota() * 0.15f / 15) * 15; // 建议下调 15%
            int newQuota = m.monthQuota() - suggestReduce;
            Map<String, Integer> newQuotaMap = new LinkedHashMap<>();
            for (Map.Entry<Section, Integer> e : monthQuotaBySection.entrySet()) {
                newQuotaMap.put(e.getKey().getCode(),
                        Math.max(0, e.getValue() - Math.round(e.getValue() * 0.15f / 15) * 15));
            }
            String quotaJson = newQuotaMap.entrySet().stream()
                    .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                    .collect(Collectors.joining(",", "{", "}"));
            String payload = "{\"suggestReduceMinutes\":" + suggestReduce
                    + ",\"newMonthQuota\":" + newQuota
                    + ",\"newQuotaBySection\":" + quotaJson + "}";
            return new Decision("SCENARIO2_REDUCE",
                    "完成率持续偏低，建议调整",
                    "近 " + LOOKBACK_DAYS + " 天平均完成率 " + m.recentAvgRate()
                            + "%，当月累计完成 " + m.monthRate()
                            + "%。可用时间可能不足或任务负荷过高。建议：①将本周剩余任务顺延至下周；②当月配额下调约 "
                            + suggestReduce + " 分钟（新配额 " + newQuota + " 分钟）。是否采纳？",
                    payload, m.todayRate());
        }
        // 场景1：少量未完成 → 迁移
        if (m.overdueTaskCount() > 0 && m.overdueTaskCount() <= FEW_UNDONE_MAX
                && m.todayRate() >= LOW_RATE_THRESHOLD) {
            List<Long> ids = m.overdueTasks().stream().map(StudyTask::getId).toList();
            // 建议迁移到明天
            LocalDate target = today.plusDays(1);
            String idJson = ids.stream().map(String::valueOf)
                    .collect(Collectors.joining(",", "[", "]"));
            String payload = "{\"taskIds\":" + idJson + ",\"targetDate\":\"" + target + "\"}";
            return new Decision("SCENARIO1_MIGRATE",
                    "少量任务未完成",
                    "有 " + m.overdueTaskCount() + " 个任务未按时完成。建议迁移到 "
                            + target + " 的合适空闲切片，保持月度目标不变。",
                    payload, m.todayRate());
        }
        // 场景3：连续超额
        if (m.recentAvgRate() > HIGH_RATE_THRESHOLD && m.todayRate() > 100) {
            // 找出最薄弱分科
            Section weakest = monthQuotaBySection.entrySet().stream()
                    .max(Comparator.comparingInt(Map.Entry::getValue))
                    .map(Map.Entry::getKey).orElse(Section.LISTENING);
            int addMinutes = 120;
            String payload = "{\"addMinutes\":" + addMinutes
                    + ",\"section\":\"" + weakest.getCode() + "\"}";
            return new Decision("SCENARIO3_ACCELERATE",
                    "连续超额完成，可加速",
                    "近 " + LOOKBACK_DAYS + " 天平均完成率 " + m.recentAvgRate()
                            + "%，今日完成率 " + m.todayRate()
                            + "%。状态很好！建议本周为「" + weakest.getDisplayName() + "」增加 "
                            + addMinutes + " 分钟任务量，提前完成月度配额，预留缓冲用于薄弱项补强。",
                    payload, m.todayRate());
        }
        return new Decision("NONE", "", "", "{}", m.todayRate());
    }

    private ProgressAdjuster() {
    }
}
