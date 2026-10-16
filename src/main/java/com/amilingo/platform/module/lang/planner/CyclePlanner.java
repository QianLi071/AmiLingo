package com.amilingo.platform.module.lang.planner;

import com.amilingo.platform.common.util.Snowflake;
import com.amilingo.platform.module.lang.enums.Section;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 长周期规划引擎：用户自定义起止周期 → 拆月度单元 → 测算每月可用时长 →
 * 生成月度工作量配额 → 下放周基准配额 → 每日类型化时间槽切片并按难度匹配任务。
 *
 * <p>约束不变量：同一自然月内，各周分科配额之和 == 该月分科配额（月末周吸收取整误差）。
 */
@Slf4j
@Component
public class CyclePlanner {

    /** 每 1.0 分差距对应的建议学习小时数（需求侧模型，公开为常量便于追溯）。 */
    private static final double HOURS_PER_BAND = 90.0;
    /** 总需求占总容量的上限（留 10% 缓冲，避免排满疲劳）。 */
    private static final double CAPACITY_USAGE = 0.9;
    /** 配额取整粒度（分钟）。 */
    private static final int GRAN = 15;
    /** 周实际排入低于配额超过该分钟数时告警。 */
    private static final int WEEK_UNMET_WARN = 60;

    private final CapacityCalculator capacityCalculator;
    private final SlotExtractor slotExtractor;
    private final TimeSlicer timeSlicer;
    private final SliceAllocator sliceAllocator;
    private final StageModel stageModel;

    public CyclePlanner(CapacityCalculator capacityCalculator,
                        SlotExtractor slotExtractor,
                        TimeSlicer timeSlicer,
                        SliceAllocator sliceAllocator,
                        StageModel stageModel) {
        this.capacityCalculator = capacityCalculator;
        this.slotExtractor = slotExtractor;
        this.timeSlicer = timeSlicer;
        this.sliceAllocator = sliceAllocator;
        this.stageModel = stageModel;
    }

    public CyclePlanResult generate(CyclePlanInput input) {
        validate(input);
        List<String> warnings = new ArrayList<>();

        // 周一对齐：把用户给的开始日期回退到当周周一，保证周/月切分整齐
        LocalDate cycleStart = input.startDate().with(DayOfWeek.MONDAY);
        LocalDate cycleEnd = input.endDate();
        Long cycleId = Snowflake.nextId();

        /* ---------- 1. 月度/周单元切分（周先切，决定月归属） ---------- */
        List<WeekPlan> weeks = splitWeeks(cycleStart, cycleEnd);
        // 仅保留至少被一个工作周（按天数多数）归属的月份，避免周期首尾残月分到配额却无法下放
        java.util.Set<String> activeMonthKeys = new java.util.LinkedHashSet<>();
        weeks.forEach(w -> activeMonthKeys.add(w.monthKey()));
        List<MonthPlan> months = new ArrayList<>();
        int monthIdx = 0;
        for (MonthPlan raw : splitMonths(cycleStart, cycleEnd)) {
            if (activeMonthKeys.contains(raw.monthKey())) {
                months.add(new MonthPlan(raw.monthKey(), monthIdx++, raw.start(), raw.end()));
            }
        }

        long totalCapacity = 0, totalDeep = 0;
        for (MonthPlan m : months) {
            CapacityCalculator.Capacity c = capacityCalculator.measure(input.userId(), m.start(), m.end());
            m.capacity = c;
            m.stage = stageModel.stageFor(m.start().plusDays(10), cycleEnd).name();
            totalCapacity += c.available();
            totalDeep += c.deep();
        }
        if (totalCapacity <= 0) {
            warnings.add("周期内未测算到可用学习时间，请先在「时间资源」中录入空闲时段。");
        }
        if (totalDeep == 0) {
            warnings.add("未标记任何 🟢 高效深度学习时段，高难度任务将只能排入 ⚪ 普通空闲，建议补标。");
        }

        /* ---------- 2. 分科总需求（差距模型 + 薄弱加成） ---------- */
        Map<Section, Integer> totalDemand = new EnumMap<>(Section.class);
        for (Section section : Section.values()) {
            double gap = gapOf(input, section);
            double boost = weakBoost(input.weakSections(), section);
            int demand = round15(gap * HOURS_PER_BAND * 60.0 * boost);
            totalDemand.put(section, demand);
        }
        long demandSum = totalDemand.values().stream().mapToInt(Integer::intValue).sum();
        double scale = 1.0;
        if (demandSum > totalCapacity * CAPACITY_USAGE && totalCapacity > 0) {
            scale = totalCapacity * CAPACITY_USAGE / demandSum;
            warnings.add(String.format(
                    "达成目标所需学习量（约 %.0f 小时）超过周期可用容量（约 %.0f 小时），已按容量下调配额至 %d%%，可考虑延长周期或降低目标。",
                    demandSum / 60.0, totalCapacity / 60.0, Math.round(scale * 100)));
        }

        /* ---------- 3. 月度配额：按「容量 × 阶段强度」加权分摊 ---------- */
        Map<Section, double[]> monthWeights = new EnumMap<>(Section.class);
        for (Section section : Section.values()) {
            double[] weights = new double[months.size()];
            double sum = 0;
            for (int i = 0; i < months.size(); i++) {
                double w = months.get(i).capacity.available() * stageFactor(months.get(i).stage);
                weights[i] = w;
                sum += w;
            }
            monthWeights.put(section, sum > 0 ? weights : new double[months.size()]);
        }
        for (Section section : Section.values()) {
            int demand = (int) Math.round(totalDemand.get(section) * scale);
            distributeAcrossUnits(months.size(), i -> monthWeights.get(section)[i], demand,
                    (i, v) -> months.get(i).quota.merge(section, v, Integer::sum));
        }
        for (MonthPlan m : months) {
            m.quotaMinutes = m.quota.values().stream().mapToInt(Integer::intValue).sum();
        }

        /* ---------- 4. 周容量测算 + 月配额下放到周 ---------- */
        for (WeekPlan w : weeks) {
            CapacityCalculator.Capacity c = capacityCalculator.measure(input.userId(), w.weekStart(), w.weekEnd());
            w.availableMinutes = (int) c.available();
        }
        for (MonthPlan m : months) {
            List<WeekPlan> monthWeeks = weeks.stream().filter(w -> w.monthKey().equals(m.monthKey())).toList();
            if (monthWeeks.isEmpty()) {
                warnings.add(String.format("%s 的周一切分落在周期外，该月配额未下放（请检查开始日期）。", m.monthKey()));
                continue;
            }
            double[] ww = monthWeeks.stream().mapToDouble(w -> Math.max(1, w.availableMinutes())).toArray();
            for (Section section : Section.values()) {
                int monthQuota = m.quota.getOrDefault(section, 0);
                distributeAcrossUnits(monthWeeks.size(), i -> ww[i], monthQuota,
                        (i, v) -> monthWeeks.get(i).quota.merge(section, v, Integer::sum));
            }
            monthWeeks.forEach(w -> w.quotaMinutes = w.quota.values().stream().mapToInt(Integer::intValue).sum());
        }

        /* ---------- 5. 周配额继续下放到「日预算」，逐周切片 + 难度匹配分配 ---------- */
        List<StudyTask> allTasks = new ArrayList<>();
        for (WeekPlan w : weeks) {
            Map<LocalDate, Map<Section, Integer>> dayBudgets = buildDayBudgets(input.userId(), w, cycleEnd);
            List<TypedTimeSlot> slots = slotExtractor.extractTyped(input.userId(), w.weekStart(), w.weekEnd());
            List<TimeSlicer.WorkSlice> slices = timeSlicer.slice(slots);
            SliceAllocator.Result result = sliceAllocator.allocate(
                    input.userId(), cycleId, w.monthKey(), w.weekStart(),
                    stageOf(months, w.monthKey()), slices, input.weakSections(), w.quota, dayBudgets);
            w.allocatedMinutes = result.allocatedMinutes();
            allTasks.addAll(result.tasks());

            int unmet = w.quotaMinutes - w.allocatedMinutes;
            if (unmet > WEEK_UNMET_WARN) {
                warnings.add(String.format("第 %d 周（%s 起）配额 %d 分钟，实际排入 %d 分钟，时间槽过碎或可用不足，缺口 %d 分钟。",
                        w.weekIndex() + 1, w.weekStart(), w.quotaMinutes, w.allocatedMinutes, unmet));
            }
        }

        PlanCycle cycle = PlanCycle.builder()
                .id(cycleId)
                .userId(input.userId())
                .examType(input.examType() != null ? input.examType().name() : "IELTS_A")
                .startDate(cycleStart)
                .endDate(cycleEnd)
                .targetTotal(input.targetTotal())
                .currentTotal(avgOf(input.currentScores()))
                .status(PlanCycle.Status.DRAFT.name())
                .build();

        return new CyclePlanResult(cycle, months, weeks, allTasks, warnings);
    }

    /* ---------------- 切分 ---------------- */

    /** 自然月与周期取交集。 */
    private static List<MonthPlan> splitMonths(LocalDate from, LocalDate to) {
        List<MonthPlan> plans = new ArrayList<>();
        YearMonth ym = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        int idx = 0;
        while (!ym.isAfter(end)) {
            LocalDate s = ym.atDay(1).isBefore(from) ? from : ym.atDay(1);
            LocalDate e = ym.atEndOfMonth().isAfter(to) ? to : ym.atEndOfMonth();
            MonthPlan p = new MonthPlan(ym.toString(), idx++, s, e);
            plans.add(p);
            ym = ym.plusMonths(1);
        }
        return plans;
    }

    /** 周一至周日与周期取交集；跨月周按在各月的实际天数多数归属（平票归周一所在月）。 */
    private static List<WeekPlan> splitWeeks(LocalDate cycleStart, LocalDate cycleEnd) {
        List<WeekPlan> plans = new ArrayList<>();
        LocalDate cursor = cycleStart;
        int idx = 0;
        while (!cursor.isAfter(cycleEnd)) {
            LocalDate end = cursor.plusDays(6);
            if (end.isAfter(cycleEnd)) end = cycleEnd;
            String monthKey;
            YearMonth startMonth = YearMonth.from(cursor);
            YearMonth endMonth = YearMonth.from(end);
            if (startMonth.equals(endMonth)) {
                monthKey = startMonth.toString();
            } else {
                long daysInStartMonth = java.time.temporal.ChronoUnit.DAYS.between(cursor, startMonth.atEndOfMonth()) + 1;
                long daysInEndMonth = end.getDayOfMonth();
                monthKey = (daysInStartMonth >= daysInEndMonth ? startMonth : endMonth).toString();
            }
            plans.add(new WeekPlan(monthKey, idx++, cursor, end));
            cursor = end.plusDays(1);
        }
        return plans;
    }

    /**
     * 把一周分科配额按各日实测可用容量比例下放到自然日（15 分钟取整，末日吸收误差），
     * 各日预算之和严格等于周配额，使任务均匀铺满整周而非堆积在周初。
     */
    private Map<LocalDate, Map<Section, Integer>> buildDayBudgets(Long userId, WeekPlan w, LocalDate cycleEnd) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = w.weekStart();
        LocalDate last = w.weekEnd().isAfter(cycleEnd) ? cycleEnd : w.weekEnd();
        while (!cursor.isAfter(last)) {
            days.add(cursor);
            cursor = cursor.plusDays(1);
        }
        double[] dayCaps = new double[days.size()];
        for (int i = 0; i < days.size(); i++) {
            dayCaps[i] = Math.max(1, capacityCalculator.measure(userId, days.get(i), days.get(i)).available());
        }
        Map<LocalDate, Map<Section, Integer>> budgets = new LinkedHashMap<>();
        for (LocalDate d : days) {
            Map<Section, Integer> b = new EnumMap<>(Section.class);
            for (Section s : Section.values()) b.put(s, 0);
            budgets.put(d, b);
        }
        for (Section section : Section.values()) {
            int weekQuota = w.quota.getOrDefault(section, 0);
            distributeAcrossUnits(days.size(), i -> dayCaps[i], weekQuota,
                    (i, v) -> budgets.get(days.get(i)).merge(section, v, Integer::sum));
        }
        return budgets;
    }

    /* ---------------- 配额分摊 ---------------- */

    /**
     * 按权重把总量分摊到 n 个单元（15 分钟取整），最后一个单元吸收取整误差，
     * 保证「各单元之和严格等于总量」。
     */
    private static void distributeAcrossUnits(int n, WeightSupplier weights, int total,
                                              CellWriter writer) {
        double sum = 0;
        for (int i = 0; i < n; i++) sum += Math.max(0, weights.get(i));
        int allocated = 0;
        for (int i = 0; i < n; i++) {
            int v;
            if (i == n - 1 || sum <= 0) {
                v = Math.max(0, total - allocated);
            } else {
                v = round15(total * (Math.max(0, weights.get(i)) / sum));
            }
            writer.write(i, v);
            allocated += v;
        }
    }

    private interface WeightSupplier { double get(int i); }
    private interface CellWriter { void write(int index, int value); }

    private static int round15(double v) {
        return Math.max(0, (int) Math.round(v / GRAN) * GRAN);
    }

    /** 阶段强度：越临近考试月度强度越高（归一化后只影响月间分配比例）。 */
    private static double stageFactor(String stageName) {
        return switch (stageName) {
            case "FOUNDATION" -> 1.0;
            case "INTENSIVE" -> 1.15;
            case "SPRINT" -> 1.3;
            default -> 1.0;
        };
    }

    private static String stageOf(List<MonthPlan> months, String monthKey) {
        return months.stream().filter(m -> m.monthKey().equals(monthKey))
                .map(m -> m.stage).findFirst().orElse("FOUNDATION");
    }

    private static double gapOf(CyclePlanInput input, Section section) {
        String code = section.getCode();
        double current = input.currentScores() != null && input.currentScores().get(code) != null
                ? input.currentScores().get(code) : 0.0;
        double target = input.targetScores() != null && input.targetScores().get(code) != null
                ? input.targetScores().get(code) : 0.0;
        return Math.max(0.0, target - current);
    }

    private static double weakBoost(List<Section> weak, Section section) {
        if (weak == null) return 1.0;
        int idx = weak.indexOf(section);
        if (idx < 0) return 1.0;
        return idx == 0 ? 1.3 : 1.15;
    }

    private static Double avgOf(Map<String, Double> scores) {
        if (scores == null || scores.isEmpty()) return null;
        return Math.round(scores.values().stream().mapToDouble(Double::doubleValue)
                .average().orElse(0) * 10) / 10.0;
    }

    private static void validate(CyclePlanInput input) {
        if (input == null || input.userId() == null) {
            throw new IllegalArgumentException("排程输入或用户 ID 不能为空");
        }
        if (input.startDate() == null || input.endDate() == null) {
            throw new IllegalArgumentException("备考周期的起止日期不能为空");
        }
        if (input.endDate().isBefore(input.startDate())) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        if (input.endDate().isAfter(input.startDate().plusDays(400))) {
            throw new IllegalArgumentException("备考周期最长支持 400 天");
        }
    }

    /* ---------------- 中间结构 ---------------- */

    /** 月度单元（容量与配额在编排中回填）。 */
    public static class MonthPlan {
        private final String monthKey;
        private final int monthIndex;
        private final LocalDate start;
        private final LocalDate end;
        private String stage;
        private CapacityCalculator.Capacity capacity = new CapacityCalculator.Capacity(0, 0, 0);
        private final Map<Section, Integer> quota = new EnumMap<>(Section.class);
        private int quotaMinutes;

        MonthPlan(String monthKey, int monthIndex, LocalDate start, LocalDate end) {
            this.monthKey = monthKey;
            this.monthIndex = monthIndex;
            this.start = start;
            this.end = end;
            for (Section s : Section.values()) quota.put(s, 0);
        }

        public String monthKey() { return monthKey; }
        public int monthIndex() { return monthIndex; }
        public LocalDate start() { return start; }
        public LocalDate end() { return end; }
        public String stage() { return stage; }
        public CapacityCalculator.Capacity capacity() { return capacity; }
        public Map<Section, Integer> quota() { return new LinkedHashMap<>(quota); }
        public int quotaMinutes() { return quotaMinutes; }
    }

    /** 周单元（容量与配额在编排中回填）。 */
    public static class WeekPlan {
        private final String monthKey;
        private final int weekIndex;
        private final LocalDate weekStart;
        private final LocalDate weekEnd;
        private int availableMinutes;
        private final Map<Section, Integer> quota = new EnumMap<>(Section.class);
        private int quotaMinutes;
        private int allocatedMinutes;

        WeekPlan(String monthKey, int weekIndex, LocalDate weekStart, LocalDate weekEnd) {
            this.monthKey = monthKey;
            this.weekIndex = weekIndex;
            this.weekStart = weekStart;
            this.weekEnd = weekEnd;
            for (Section s : Section.values()) quota.put(s, 0);
        }

        public String monthKey() { return monthKey; }
        public int weekIndex() { return weekIndex; }
        public LocalDate weekStart() { return weekStart; }
        public LocalDate weekEnd() { return weekEnd; }
        public int availableMinutes() { return availableMinutes; }
        public Map<Section, Integer> quota() { return new LinkedHashMap<>(quota); }
        public int quotaMinutes() { return quotaMinutes; }
        public int allocatedMinutes() { return allocatedMinutes; }
    }
}
