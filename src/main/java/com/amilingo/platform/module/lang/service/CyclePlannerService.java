package com.amilingo.platform.module.lang.service;

import com.amilingo.platform.module.lang.dto.request.CyclePlanRequest;
import com.amilingo.platform.module.lang.dto.request.TaskProgressRequest;
import com.amilingo.platform.module.lang.dto.request.TaskUpdateRequest;
import com.amilingo.platform.module.lang.dto.response.CycleView;
import com.amilingo.platform.module.lang.dto.response.TaskPoolView;
import com.amilingo.platform.module.lang.entity.ExamGoal;
import com.amilingo.platform.module.lang.enums.ExamType;
import com.amilingo.platform.module.lang.enums.Section;
import com.amilingo.platform.module.lang.event.TaskProgressChangedEvent;
import com.amilingo.platform.module.lang.planner.CyclePlanInput;
import com.amilingo.platform.module.lang.planner.CyclePlanResult;
import com.amilingo.platform.module.lang.planner.CyclePlanner;
import com.amilingo.platform.module.lang.planner.PlanAdjustment;
import com.amilingo.platform.module.lang.planner.PlanCycle;
import com.amilingo.platform.module.lang.planner.PlanMonth;
import com.amilingo.platform.module.lang.planner.PlanWeek;
import com.amilingo.platform.module.lang.planner.ProgressAdjuster;
import com.amilingo.platform.module.lang.planner.SlotMigrationPlanner;
import com.amilingo.platform.module.lang.planner.StudyTask;
import com.amilingo.platform.module.lang.planner.TaskPoolCatalog;
import com.amilingo.platform.module.lang.repository.ExamGoalRepository;
import com.amilingo.platform.module.lang.repository.PlanAdjustmentRepository;
import com.amilingo.platform.module.lang.repository.PlanCycleRepository;
import com.amilingo.platform.module.lang.repository.PlanMonthRepository;
import com.amilingo.platform.module.lang.repository.PlanWeekRepository;
import com.amilingo.platform.module.lang.repository.StudyTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 长周期规划服务：生成草稿周期、持久化月/周配额与切片任务、采纳/拒绝、手动修改、进度回调与动态调整。 */
@Slf4j
@Service
public class CyclePlannerService {

    /** 同一周期内允许系统自动降档（场景2）的最大次数，第 3 次必须转人工确认。 */
    private static final long MAX_AUTO_REDUCE_TIMES = 2;
    /** 自动调整记录在视图中暴露的状态。 */
    private static final List<String> VISIBLE_ADJUST_STATUSES = List.of("PENDING", "AUTO_EXECUTED");

    private final CyclePlanner cyclePlanner;
    private final TaskPoolCatalog taskPoolCatalog;
    private final PlanCycleRepository cycleRepository;
    private final PlanMonthRepository monthRepository;
    private final PlanWeekRepository weekRepository;
    private final StudyTaskRepository taskRepository;
    private final ExamGoalRepository examGoalRepository;
    private final PlanAdjustmentRepository adjustmentRepository;
    private final SlotMigrationPlanner slotMigrationPlanner;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public CyclePlannerService(CyclePlanner cyclePlanner,
            TaskPoolCatalog taskPoolCatalog,
            PlanCycleRepository cycleRepository,
            PlanMonthRepository monthRepository,
            PlanWeekRepository weekRepository,
            StudyTaskRepository taskRepository,
            ExamGoalRepository examGoalRepository,
            PlanAdjustmentRepository adjustmentRepository,
            SlotMigrationPlanner slotMigrationPlanner,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.cyclePlanner = cyclePlanner;
        this.taskPoolCatalog = taskPoolCatalog;
        this.cycleRepository = cycleRepository;
        this.monthRepository = monthRepository;
        this.weekRepository = weekRepository;
        this.taskRepository = taskRepository;
        this.examGoalRepository = examGoalRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.slotMigrationPlanner = slotMigrationPlanner;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /* ---------------- 生成 ---------------- */

    @Transactional
    public CycleView generate(Long userId, CyclePlanRequest request) {
        ExamGoal storedGoal = examGoalRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).orElse(null);

        ExamType examType = request.examType() != null && !request.examType().isBlank()
                ? ExamType.fromCode(request.examType())
                : (storedGoal != null ? storedGoal.getExamType() : ExamType.IELTS_A);

        LocalDate startDate = request.startDate() != null && !request.startDate().isBlank()
                ? LocalDate.parse(request.startDate())
                : LocalDate.now();
        LocalDate endDate = request.endDate() != null && !request.endDate().isBlank()
                ? LocalDate.parse(request.endDate())
                : (storedGoal != null ? storedGoal.getExamDate() : LocalDate.now().plusDays(90));

        Map<String, Double> current = request.current() != null
                ? new LinkedHashMap<>(request.current())
                : new LinkedHashMap<>(Map.of("L", 5.0, "R", 5.5, "W", 5.0, "S", 5.0));
        Map<String, Double> target = request.target() != null
                ? new LinkedHashMap<>(request.target())
                : new LinkedHashMap<>(Map.of("L", 6.5, "R", 6.5, "W", 6.0, "S", 6.0));
        Double targetTotal = request.targetTotal() != null ? request.targetTotal()
                : (storedGoal != null ? storedGoal.getTargetTotal() : 6.5);
        List<Section> weak = resolveWeak(request.weak(), current, target);

        CyclePlanInput input = new CyclePlanInput(userId, examType, startDate, endDate,
                current, target, targetTotal, weak);
        CyclePlanResult result = cyclePlanner.generate(input);

        // 只清理旧的 DRAFT 草稿（草稿可覆盖重算）；ACCEPTED 执行中的周期及其任务/调整历史保留，
        // 待新草稿被采纳时再归档（见 accept()），避免「生成后反悔」导致旧计划与进度记录被毁。
        List<PlanCycle> oldCycles = cycleRepository.findByUserId(userId);
        for (PlanCycle old : oldCycles) {
            if (!PlanCycle.Status.DRAFT.name().equals(old.getStatus()))
                continue;
            taskRepository.deleteByCycleId(old.getId());
            weekRepository.deleteByCycleId(old.getId());
            monthRepository.deleteByCycleId(old.getId());
            adjustmentRepository.deleteByCycleId(old.getId());
            cycleRepository.delete(old);
        }
        taskRepository.deleteLegacyByUserId(userId); // 清理无 cycle 的旧引擎遗留任务（不动已归属周期的任务）

        cycleRepository.save(result.cycle());
        for (CyclePlanner.MonthPlan m : result.months()) {
            monthRepository.save(PlanMonth.builder()
                    .cycleId(result.cycle().getId())
                    .userId(userId)
                    .monthKey(m.monthKey())
                    .monthIndex(m.monthIndex())
                    .startDate(m.start())
                    .endDate(m.end())
                    .stage(m.stage())
                    .availableMinutes((int) m.capacity().available())
                    .deepMinutes((int) m.capacity().deep())
                    .fragmentMinutes((int) m.capacity().fragment())
                    .quotaMinutes(m.quotaMinutes())
                    .quotaJson(writeQuota(m.quota()))
                    .build());
        }
        for (CyclePlanner.WeekPlan w : result.weeks()) {
            weekRepository.save(PlanWeek.builder()
                    .cycleId(result.cycle().getId())
                    .userId(userId)
                    .monthKey(w.monthKey())
                    .weekIndex(w.weekIndex())
                    .weekStart(w.weekStart())
                    .weekEnd(w.weekEnd())
                    .availableMinutes(w.availableMinutes())
                    .quotaMinutes(w.quotaMinutes())
                    .allocatedMinutes(w.allocatedMinutes())
                    .quotaJson(writeQuota(w.quota()))
                    .build());
        }
        taskRepository.saveAll(result.tasks());

        return toViewFromPlan(result.cycle(), result.months(), result.weeks(), result.tasks(), result.warnings());
    }

    /* ---------------- 查询 / 采纳 / 拒绝 ---------------- */

    @Transactional(readOnly = true)
    public CycleView getCurrent(Long userId) {
        PlanCycle cycle = cycleRepository
                .findFirstByUserIdAndStatusNotOrderByCreatedAtDesc(userId, PlanCycle.Status.ARCHIVED.name())
                .orElse(null);
        if (cycle == null) {
            return null;
        }
        List<PlanMonth> months = monthRepository.findByCycleIdOrderByMonthIndex(cycle.getId());
        List<PlanWeek> weeks = weekRepository.findByCycleIdOrderByWeekIndex(cycle.getId());
        List<StudyTask> tasks = taskRepository.findByCycleIdOrderByScheduledStart(cycle.getId());
        // 待处理建议 + 最近自动执行（可撤销）的调整
        List<PlanAdjustment> adjustments = adjustmentRepository
                .findTop20ByCycleIdAndStatusInOrderByCreatedAtDesc(cycle.getId(), VISIBLE_ADJUST_STATUSES);
        return toViewFromEntities(cycle, months, weeks, tasks, List.of(), adjustments);
    }

    @Transactional
    public CycleView accept(Long userId, Long cycleId) {
        PlanCycle cycle = cycleRepository.findByIdAndUserId(cycleId, userId)
                .orElseThrow(() -> new IllegalArgumentException("规划不存在"));
        // 采纳新草稿前，把旧的执行中周期归档（数据保留，不再展示），而非删除。
        for (PlanCycle old : cycleRepository.findByUserId(userId)) {
            if (!old.getId().equals(cycleId)
                    && PlanCycle.Status.ACCEPTED.name().equals(old.getStatus())) {
                old.setStatus(PlanCycle.Status.ARCHIVED.name());
                cycleRepository.save(old);
            }
        }
        cycle.setStatus(PlanCycle.Status.ACCEPTED.name());
        cycleRepository.save(cycle);
        return getCurrent(userId);
    }

    @Transactional
    public CycleView reject(Long userId, Long cycleId) {
        PlanCycle cycle = cycleRepository.findByIdAndUserId(cycleId, userId)
                .orElseThrow(() -> new IllegalArgumentException("规划不存在"));
        if (!PlanCycle.Status.DRAFT.name().equals(cycle.getStatus())) {
            throw new IllegalArgumentException("仅草稿可拒绝，执行中的计划请使用删除");
        }
        taskRepository.deleteByCycleId(cycleId);
        weekRepository.deleteByCycleId(cycleId);
        monthRepository.deleteByCycleId(cycleId);
        adjustmentRepository.deleteByCycleId(cycleId);
        cycleRepository.delete(cycle);
        return null;
    }

    /* ---------------- 手动修改 / 删除 ---------------- */

    @Transactional
    public CycleView updateTask(Long userId, Long taskId, TaskUpdateRequest req) {
        StudyTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        LocalDateTime start = task.getScheduledStart();
        LocalDateTime end = task.getScheduledEnd();
        if (req.start() != null && !req.start().isBlank())
            start = parseDateTime(task, req.start());
        if (req.end() != null && !req.end().isBlank())
            end = parseDateTime(task, req.end());
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }
        final LocalDateTime finalStart = start;
        final LocalDateTime finalEnd = end;
        // 同日任务重叠校验
        List<StudyTask> siblings = taskRepository.findByCycleIdOrderByScheduledStart(task.getCycleId()).stream()
                .filter(t -> !t.getId().equals(taskId))
                .filter(t -> t.getScheduledStart().toLocalDate().equals(finalStart.toLocalDate()))
                .toList();
        for (StudyTask other : siblings) {
            if (finalStart.isBefore(other.getScheduledEnd()) && finalEnd.isAfter(other.getScheduledStart())) {
                throw new IllegalArgumentException(String.format("与任务「%s」（%s-%s）时间重叠",
                        other.getTitle(),
                        other.getScheduledStart().toLocalTime(),
                        other.getScheduledEnd().toLocalTime()));
            }
        }
        if (req.title() != null && !req.title().isBlank())
            task.setTitle(req.title().trim());
        if (req.objective() != null)
            task.setObjective(req.objective().trim());
        task.setScheduledStart(start);
        task.setScheduledEnd(end);
        task.setDurationMinutes((int) java.time.Duration.between(start, end).toMinutes());
        taskRepository.save(task);
        recomputeWeekAllocated(task.getCycleId(), task.getWeekStart());
        return getCurrent(userId);
    }

    @Transactional
    public CycleView deleteTask(Long userId, Long taskId) {
        StudyTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        Long cycleId = task.getCycleId();
        LocalDate weekStart = task.getWeekStart();
        taskRepository.delete(task);
        if (cycleId != null && weekStart != null) {
            recomputeWeekAllocated(cycleId, weekStart);
        }
        return getCurrent(userId);
    }

    /* ---------------- 任务模块进度回调（接口约定①） ---------------- */

    @Transactional
    public CycleView reportProgress(Long userId, Long taskId, TaskProgressRequest req) {
        StudyTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        String status = req.status() == null ? "TODO" : req.status().trim().toUpperCase();
        if (!List.of("TODO", "PARTIAL", "DONE").contains(status)) {
            throw new IllegalArgumentException("任务状态必须为 DONE / PARTIAL / TODO");
        }
        task.setProgressStatus(status);
        task.setDone("DONE".equals(status));
        if (req.actualMinutes() != null) {
            task.setActualMinutes(Math.max(0, req.actualMinutes()));
        } else if ("DONE".equals(status)) {
            task.setActualMinutes(task.getDurationMinutes());
        }
        taskRepository.save(task);
        // 进度落库后发事件：自动重排由 AutoReplanHandler 在事务提交后异步消费
        // （槽位扫描/债务降档不拖慢勾选接口，失败也不影响进度记录）。
        eventPublisher.publishEvent(
                new TaskProgressChangedEvent(userId, task.getCycleId(), taskId, status));
        return getCurrent(userId);
    }

    /* ---------------- 任务池读取（接口约定②） ---------------- */

    /** 任务池只读视图：任务类型、预估工作量、难度标签，供任务模块与前端查询。 */
    @Transactional(readOnly = true)
    public TaskPoolView taskPool() {
        List<TaskPoolView.Entry> entries = taskPoolCatalog.all().stream()
                .map(e -> new TaskPoolView.Entry(
                        e.code(), e.section().getCode(), e.difficulty().name(), e.minutes(),
                        e.taskType(), e.title(), e.objective()))
                .toList();
        return new TaskPoolView(entries);
    }

    /* ---------------- 自动/手动调整（第三节核心） ---------------- */

    /** 一次周期评估的中间结果。 */
    record Evaluation(ProgressAdjuster.Metrics metrics, ProgressAdjuster.Decision decision,
            PlanMonth month, Map<Section, Integer> quotaBySection) {
    }

    /** 场景1自动迁移的执行结果。 */
    record MigrationExecution(SlotMigrationPlanner.Result plan, String snapshotJson, int migratedCount) {
    }

    /**
     * 进度事件驱动的自动重排（由 AutoReplanHandler 在独立线程/事务中调用）。
     *
     * <p>
     * 按周期 {@code autoAdjustLevel} 分级：
     * OFF 全部落 PENDING；SMART 仅场景1自动；FULL 场景1/2/3 自动。
     * 同场景已有 PENDING（用户尚未处理）时跳过，避免重复刷。
     */
    @Transactional
    public void runAutoReplan(Long userId, Long cycleId) {
        PlanCycle cycle = cycleRepository.findById(cycleId).orElse(null);
        if (cycle == null || !userId.equals(cycle.getUserId()))
            return;
        String level = normalizeLevel(cycle.getAutoAdjustLevel());

        LocalDate today = LocalDate.now();
        Evaluation eval = evaluateCycle(cycleId, today);
        String scenario = eval.decision().scenario();
        if ("NONE".equals(scenario))
            return;
        if (adjustmentRepository.existsByCycleIdAndScenarioAndStatus(cycleId, scenario, "PENDING"))
            return;

        // 预警永远不自动执行；OFF 全手动；SMART 只自动场景1；FULL 三场景全自动
        boolean automatic = !"PILE_UP_WARNING".equals(scenario) && switch (level) {
            case "OFF" -> false;
            case "FULL" -> true;
            default -> "SCENARIO1_MIGRATE".equals(scenario);
        };

        if (!automatic) {
            saveAdjustment(cycle, scenario, eval.decision().title(), eval.decision().description(),
                    eval.decision().payloadJson(), eval.decision().triggerRate(),
                    "PENDING", "MANUAL", null);
            return;
        }

        switch (scenario) {
            case "SCENARIO1_MIGRATE" -> {
                MigrationExecution executed = executeAutoMigrate(cycle, eval, today);
                List<Long> unresolved = executed.plan().unresolvedTaskIds();
                // 一个都没迁走时不留「已自动迁移」假记录，直接交给债务级联/人工建议
                if (executed.migratedCount() > 0) {
                    if (executed.snapshotJson() == null) {
                        // 与迁移同事务：抛错回滚任务改动，绝不留无法撤销的自动调整
                        throw new IllegalStateException("自动迁移快照写入失败，中止本次自动重排");
                    }
                    LocalDate firstDate = executed.plan().moves().stream()
                            .map(m -> m.newStart().toLocalDate())
                            .min(Comparator.naturalOrder())
                            .orElse(today.plusDays(1));
                    String description = String.format(
                            "已自动把 %d 个未完成任务移入 %s 起的合适空闲时段（按难度匹配 🟢/⚪/🟡 槽位，且不与既有安排冲突）。",
                            executed.migratedCount(), firstDate)
                            + (unresolved.isEmpty() ? ""
                                    : String.format("另有 %d 个任务未来两周暂无合格时段。", unresolved.size()))
                            + "可点「撤销」恢复原时间。";
                    String payload = "{\"migratedCount\":" + executed.migratedCount()
                            + ",\"targetDate\":\"" + firstDate + "\""
                            + ",\"unresolvedCount\":" + unresolved.size() + "}";
                    saveAdjustment(cycle, scenario, "少量任务未完成，已自动迁移", description, payload,
                            eval.decision().triggerRate(), "AUTO_EXECUTED", "AUTO", executed.snapshotJson());
                }
                // 第一层迁移装不下 → 第二层债务级联（结转受限/降档次数受限，可能仍转人工）
                if (!unresolved.isEmpty()) {
                    cascadeDebtReduce(cycle, eval, unresolved, level);
                }
            }
            case "SCENARIO2_REDUCE" -> {
                long used = adjustmentRepository.countByCycleIdAndScenarioAndSourceAndStatus(
                        cycleId, scenario, "AUTO", "AUTO_EXECUTED");
                String reducePayload = buildReducePayload(eval);
                if (used < MAX_AUTO_REDUCE_TIMES) {
                    MonthSnapshot monthSnap = applyReduceToMonth(cycleId, reducePayload, today);
                    if (monthSnap == null) {
                        log.warn("自动降档跳过：当月计划不存在或 payload 无 newMonthQuota cycleId={}", cycleId);
                        return;
                    }
                    String snapshot = writeSnapshot(new Snapshot(List.of(), List.of(), List.of(monthSnap)));
                    if (snapshot == null) {
                        throw new IllegalStateException("自动降档快照写入失败，中止本次自动重排");
                    }
                    String description = String.format(
                            "近几天完成率持续偏低，已自动下调当月配额约 15%%（本周期第 %d 次自动降档，最多 %d 次），可撤销恢复。",
                            used + 1, MAX_AUTO_REDUCE_TIMES);
                    saveAdjustment(cycle, scenario, "完成率持续偏低，已自动下调配额", description,
                            reducePayload, eval.decision().triggerRate(),
                            "AUTO_EXECUTED", "AUTO", snapshot);
                } else {
                    saveAdjustment(cycle, scenario, eval.decision().title(),
                            eval.decision().description()
                                    + "（本周期已自动降档 " + MAX_AUTO_REDUCE_TIMES
                                    + " 次达上限，本次需你手动确认）",
                            reducePayload, eval.decision().triggerRate(),
                            "PENDING", "MANUAL", null);
                }
            }
            case "SCENARIO3_ACCELERATE" -> {
                WeekSnapshot weekSnap = applyAccelerateToWeek(cycleId, eval.decision().payloadJson(), today);
                if (weekSnap == null) {
                    log.warn("自动加速跳过：本周计划不存在或 payload 缺字段 cycleId={}", cycleId);
                    return;
                }
                String snapshot = writeSnapshot(new Snapshot(List.of(), List.of(weekSnap), List.of()));
                if (snapshot == null) {
                    throw new IllegalStateException("自动加速快照写入失败，中止本次自动重排");
                }
                saveAdjustment(cycle, scenario, eval.decision().title(),
                        eval.decision().description() + "（已自动执行，可撤销）",
                        eval.decision().payloadJson(), eval.decision().triggerRate(),
                        "AUTO_EXECUTED", "AUTO", snapshot);
            }
            default -> {
                // PILE_UP_WARNING 已在上面落 PENDING，其余未知场景不自动处理
            }
        }
    }

    /** 读取周期进度指标并产出决策。 */
    private Evaluation evaluateCycle(Long cycleId, LocalDate today) {
        List<StudyTask> tasks = taskRepository.findByCycleIdOrderByScheduledStart(cycleId);
        String monthKey = today.toString().substring(0, 7);
        PlanMonth month = monthRepository.findByCycleIdOrderByMonthIndex(cycleId).stream()
                .filter(m -> monthKey.equals(m.getMonthKey()))
                .findFirst()
                .orElse(null);
        int monthQuota = month != null ? month.getQuotaMinutes() : 0;
        Map<Section, Integer> quotaBySection = new LinkedHashMap<>();
        if (month != null) {
            readQuota(month.getQuotaJson()).forEach((code, v) -> {
                try {
                    quotaBySection.put(Section.fromCode(code), v);
                } catch (IllegalArgumentException ignored) {
                }
            });
        }
        ProgressAdjuster.Metrics metrics = ProgressAdjuster.computeMetrics(tasks, monthQuota, today);
        ProgressAdjuster.Decision decision = ProgressAdjuster.decide(metrics, cycleId, today, quotaBySection);
        return new Evaluation(metrics, decision, month, quotaBySection);
    }

    /** 场景1自动执行：槽位匹配迁移，返回落点计划与迁移前任务快照。 */
    private MigrationExecution executeAutoMigrate(PlanCycle cycle, Evaluation eval, LocalDate today) {
        List<StudyTask> moving = eval.metrics().overdueTasks();
        LocalDate searchFrom = today.plusDays(1);
        long span = java.time.temporal.ChronoUnit.DAYS.between(searchFrom, cycle.getEndDate()) + 1;
        int searchDays = (int) Math.max(1, Math.min(SlotMigrationPlanner.DEFAULT_SEARCH_DAYS, span));
        int debtCap = nextWeekDebtCap(cycle.getId(), today);

        SlotMigrationPlanner.Result plan = slotMigrationPlanner.plan(
                cycle.getUserId(), cycle.getId(), moving, searchFrom, searchDays, debtCap);

        List<TaskSnapshot> taskSnaps = new ArrayList<>();
        Set<LocalDate> affectedWeeks = new HashSet<>();
        List<StudyTask> migrated = new ArrayList<>();
        for (SlotMigrationPlanner.Move m : plan.moves()) {
            StudyTask t = taskRepository.findById(m.taskId()).orElse(null);
            if (t == null)
                continue;
            taskSnaps.add(new TaskSnapshot(t.getId(), t.getScheduledStart().toString(),
                    t.getScheduledEnd().toString(),
                    t.getWeekStart() != null ? t.getWeekStart().toString() : null, t.getSlotType()));
            if (t.getWeekStart() != null)
                affectedWeeks.add(t.getWeekStart());
            affectedWeeks.add(m.newWeekStart());
            t.setScheduledStart(m.newStart());
            t.setScheduledEnd(m.newEnd());
            t.setWeekStart(m.newWeekStart());
            t.setMonthKey(m.newStart().toString().substring(0, 7));
            t.setSlotType(m.slotKind());
            migrated.add(t);
        }
        taskRepository.saveAll(migrated);
        affectedWeeks.forEach(w -> recomputeWeekAllocated(cycle.getId(), w));
        return new MigrationExecution(plan, writeSnapshot(new Snapshot(taskSnaps, List.of(), List.of())),
                migrated.size());
    }

    /** 下周债务承接上限：下周配额的 20%；周期内不存在下周则为 0（无处承接）。 */
    private int nextWeekDebtCap(Long cycleId, LocalDate today) {
        LocalDate nextWeekStart = SlotMigrationPlanner.weekStart(today).plusWeeks(1);
        return weekRepository.findByCycleIdOrderByWeekIndex(cycleId).stream()
                .filter(w -> w.getWeekStart().equals(nextWeekStart))
                .findFirst()
                .map(w -> (int) Math.round(w.getQuotaMinutes() * SlotMigrationPlanner.NEXT_WEEK_DEBT_RATIO))
                .orElse(0);
    }

    /** 第二层：迁移仍装不下的任务构成学习债务 → FULL 自动降档（同周期≤2次），其余转人工。 */
    private void cascadeDebtReduce(PlanCycle cycle, Evaluation eval, List<Long> unresolved, String level) {
        String scenario = "SCENARIO2_REDUCE";
        if (adjustmentRepository.existsByCycleIdAndScenarioAndStatus(cycle.getId(), scenario, "PENDING"))
            return;
        long used = adjustmentRepository.countByCycleIdAndScenarioAndSourceAndStatus(
                cycle.getId(), scenario, "AUTO", "AUTO_EXECUTED");
        String payload = buildReducePayload(eval);
        if ("FULL".equals(level) && used < MAX_AUTO_REDUCE_TIMES) {
            MonthSnapshot monthSnap = applyReduceToMonth(cycle.getId(), payload, LocalDate.now());
            if (monthSnap == null) {
                log.warn("债务级联自动降档跳过：当月计划不存在 cycleId={}", cycle.getId());
                return;
            }
            String snapshot = writeSnapshot(new Snapshot(List.of(), List.of(), List.of(monthSnap)));
            if (snapshot == null) {
                throw new IllegalStateException("债务级联降档快照写入失败，中止本次自动重排");
            }
            String description = String.format(
                    "有 %d 个未完成任务在未来两周找不到合格时段（学习债务溢出），已自动下调当月配额约 15%%"
                            + "（本周期第 %d 次自动降档，最多 %d 次）；剩余任务请在时间资源扩容后手动安排，可撤销恢复。",
                    unresolved.size(), used + 1, MAX_AUTO_REDUCE_TIMES);
            saveAdjustment(cycle, scenario, "学习债务溢出，已自动下调配额", description, payload,
                    eval.decision().triggerRate(), "AUTO_EXECUTED", "AUTO", snapshot);
        } else {
            String extra = used >= MAX_AUTO_REDUCE_TIMES
                    ? String.format("本周期已自动降档 %d 次达上限，本次需你手动确认。", MAX_AUTO_REDUCE_TIMES)
                    : "当前自动调整级别为 SMART/OFF，降档需你手动确认。";
            String description = String.format(
                    "有 %d 个未完成任务在未来两周找不到合格时段，建议将本周剩余任务顺延并下调当月配额约 15%%。%s",
                    unresolved.size(), extra);
            saveAdjustment(cycle, scenario, "学习债务堆积，建议下调配额", description, payload,
                    eval.decision().triggerRate(), "PENDING", "MANUAL", null);
        }
    }

    /** 按当月配额构造 15% 降档 payload（与 ProgressAdjuster 场景2口径一致）。 */
    private String buildReducePayload(Evaluation eval) {
        int monthQuota = eval.month() != null ? eval.month().getQuotaMinutes() : eval.metrics().monthQuota();
        int suggestReduce = Math.round(monthQuota * 0.15f / 15) * 15;
        int newQuota = monthQuota - suggestReduce;
        Map<String, Integer> newQuotaMap = new LinkedHashMap<>();
        for (Map.Entry<Section, Integer> e : eval.quotaBySection().entrySet()) {
            newQuotaMap.put(e.getKey().getCode(),
                    Math.max(0, e.getValue() - Math.round(e.getValue() * 0.15f / 15) * 15));
        }
        String quotaJson = newQuotaMap.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        return "{\"suggestReduceMinutes\":" + suggestReduce
                + ",\"newMonthQuota\":" + newQuota
                + ",\"newQuotaBySection\":" + quotaJson + "}";
    }

    /** 撤销一条自动执行的调整：按 beforeSnapshot 回滚任务时间与周/月配额。 */
    @Transactional
    public CycleView undoAdjustment(Long userId, Long adjustmentId) {
        PlanAdjustment adj = adjustmentRepository.findById(adjustmentId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("调整记录不存在"));
        if (!"AUTO_EXECUTED".equals(adj.getStatus())) {
            throw new IllegalArgumentException("仅自动执行的调整可以撤销");
        }
        if (adj.getBeforeSnapshot() == null || adj.getBeforeSnapshot().isBlank()) {
            throw new IllegalArgumentException("该调整缺少撤销快照，无法自动恢复");
        }
        restoreSnapshot(adj.getCycleId(), adj.getBeforeSnapshot());
        adj.setStatus("REVOKED");
        adjustmentRepository.save(adj);
        return getCurrent(userId);
    }

    /** 查询当前周期（或指定周期）的自动调整级别。 */
    @Transactional(readOnly = true)
    public String getAutoAdjustLevel(Long userId, Long cycleId) {
        PlanCycle cycle = loadCycle(userId, cycleId);
        return cycle == null ? null : normalizeLevel(cycle.getAutoAdjustLevel());
    }

    /** 更新自动调整级别：OFF / SMART / FULL。 */
    @Transactional
    public String updateAutoAdjustLevel(Long userId, Long cycleId, String level) {
        String normalized = normalizeLevel(level);
        PlanCycle cycle = loadCycle(userId, cycleId);
        if (cycle == null) {
            throw new IllegalArgumentException("规划不存在");
        }
        cycle.setAutoAdjustLevel(normalized);
        cycleRepository.save(cycle);
        return normalized;
    }

    private PlanCycle loadCycle(Long userId, Long cycleId) {
        if (cycleId != null) {
            return cycleRepository.findByIdAndUserId(cycleId, userId).orElse(null);
        }
        return cycleRepository.findFirstByUserIdAndStatusNotOrderByCreatedAtDesc(
                userId, PlanCycle.Status.ARCHIVED.name()).orElse(null);
    }

    private static String normalizeLevel(String level) {
        if (level == null || level.isBlank())
            return "SMART";
        String v = level.trim().toUpperCase();
        if (!List.of("OFF", "SMART", "FULL").contains(v)) {
            throw new IllegalArgumentException("自动调整级别必须为 OFF / SMART / FULL");
        }
        return v;
    }

    private void saveAdjustment(PlanCycle cycle, String scenario, String title, String description,
            String payload, Integer triggerRate, String status,
            String source, String beforeSnapshot) {
        adjustmentRepository.save(PlanAdjustment.builder()
                .cycleId(cycle.getId())
                .userId(cycle.getUserId())
                .scenario(scenario)
                .title(truncate(title, 200))
                .description(truncate(description, 1000))
                .payload(payload)
                .triggerRate(triggerRate)
                .triggerDate(LocalDateTime.now())
                .status(status)
                .source(source)
                .beforeSnapshot(beforeSnapshot)
                .build());
    }

    private static String truncate(String s, int max) {
        if (s == null)
            return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 查询当前周期的待处理调整建议。 */
    @Transactional(readOnly = true)
    public List<PlanAdjustment> pendingAdjustments(Long userId) {
        PlanCycle cycle = cycleRepository
                .findFirstByUserIdAndStatusNotOrderByCreatedAtDesc(userId, PlanCycle.Status.ARCHIVED.name())
                .orElse(null);
        if (cycle == null)
            return List.of();
        return adjustmentRepository.findByCycleIdAndStatusOrderByCreatedAtDesc(cycle.getId(), "PENDING");
    }

    /** 采纳调整建议并执行对应动作。 */
    @Transactional
    public CycleView acceptAdjustment(Long userId, Long adjustmentId) {
        PlanAdjustment adj = adjustmentRepository.findById(adjustmentId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("建议不存在"));
        if (!"PENDING".equals(adj.getStatus())) {
            throw new IllegalArgumentException("建议已被处理");
        }
        executeAdjustment(adj);
        adj.setStatus("ACCEPTED");
        adjustmentRepository.save(adj);
        return getCurrent(userId);
    }

    /** 拒绝调整建议。 */
    @Transactional
    public CycleView rejectAdjustment(Long userId, Long adjustmentId) {
        PlanAdjustment adj = adjustmentRepository.findById(adjustmentId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("建议不存在"));
        if (!"PENDING".equals(adj.getStatus())) {
            throw new IllegalArgumentException("建议已被处理");
        }
        adj.setStatus("REJECTED");
        adjustmentRepository.save(adj);
        return getCurrent(userId);
    }

    /** 根据场景类型执行真正的调整动作（用户手动采纳路径，不留快照、不可撤销）。 */
    private void executeAdjustment(PlanAdjustment adj) {
        LocalDate today = LocalDate.now();
        switch (adj.getScenario()) {
            case "SCENARIO1_MIGRATE" -> executeManualMigrate(adj, today);
            case "SCENARIO2_REDUCE" -> applyReduceToMonth(adj.getCycleId(), adj.getPayload(), today);
            case "SCENARIO3_ACCELERATE" -> applyAccelerateToWeek(adj.getCycleId(), adj.getPayload(), today);
            case "PILE_UP_WARNING" -> {
                // 纯预警，无需执行动作
            }
            default -> {
            }
        }
    }

    /**
     * 场景1手动采纳：同样走槽位匹配迁移（修掉旧的同时刻平移会撞上课时间的缺陷）。
     * 建议可能在生成后几天才被采纳，搜索起点取「明天/建议目标日」中较晚者；装不下的任务保留原位。
     */
    private void executeManualMigrate(PlanAdjustment adj, LocalDate today) {
        String payload = adj.getPayload();
        LocalDate targetDate = extractJsonDate(payload, "targetDate");
        List<Long> taskIds = extractJsonLongArray(payload, "taskIds");
        if (taskIds.isEmpty())
            return;
        LocalDate searchFrom = today.plusDays(1);
        if (targetDate != null && targetDate.isAfter(searchFrom))
            searchFrom = targetDate;

        List<StudyTask> moving = new ArrayList<>();
        for (Long taskId : taskIds) {
            StudyTask t = taskRepository.findById(taskId).orElse(null);
            if (t != null)
                moving.add(t);
        }
        if (moving.isEmpty())
            return;
        PlanCycle cycle = cycleRepository.findById(adj.getCycleId()).orElse(null);
        LocalDate endBound = cycle != null ? cycle.getEndDate() : searchFrom.plusDays(13);
        long span = java.time.temporal.ChronoUnit.DAYS.between(searchFrom, endBound) + 1;
        int searchDays = (int) Math.max(1, Math.min(SlotMigrationPlanner.DEFAULT_SEARCH_DAYS, span));

        SlotMigrationPlanner.Result plan = slotMigrationPlanner.plan(
                adj.getUserId(), adj.getCycleId(), moving, searchFrom,
                searchDays, nextWeekDebtCap(adj.getCycleId(), today));
        Set<LocalDate> affectedWeeks = new HashSet<>();
        List<StudyTask> migrated = new ArrayList<>();
        for (SlotMigrationPlanner.Move m : plan.moves()) {
            StudyTask t = taskRepository.findById(m.taskId()).orElse(null);
            if (t == null)
                continue;
            if (t.getWeekStart() != null)
                affectedWeeks.add(t.getWeekStart());
            affectedWeeks.add(m.newWeekStart());
            t.setScheduledStart(m.newStart());
            t.setScheduledEnd(m.newEnd());
            t.setWeekStart(m.newWeekStart());
            t.setMonthKey(m.newStart().toString().substring(0, 7));
            t.setSlotType(m.slotKind());
            migrated.add(t);
        }
        taskRepository.saveAll(migrated);
        affectedWeeks.forEach(w -> recomputeWeekAllocated(adj.getCycleId(), w));
    }

    /** 场景2：下调当月配额及分科配额，返回执行前的月份快照。 */
    MonthSnapshot applyReduceToMonth(Long cycleId, String payload, LocalDate today) {
        Integer newQuota = extractJsonInt(payload, "newMonthQuota");
        if (newQuota == null)
            return null;
        String monthKey = today.toString().substring(0, 7);
        PlanMonth m = monthRepository.findByCycleIdOrderByMonthIndex(cycleId).stream()
                .filter(mm -> monthKey.equals(mm.getMonthKey()))
                .findFirst().orElse(null);
        if (m == null)
            return null;
        MonthSnapshot snapshot = new MonthSnapshot(m.getMonthKey(), m.getQuotaMinutes(), m.getQuotaJson());
        m.setQuotaMinutes(newQuota);
        Map<String, Integer> newBySection = extractJsonIntMap(payload, "newQuotaBySection");
        if (!newBySection.isEmpty()) {
            Map<Section, Integer> quota = new LinkedHashMap<>();
            newBySection.forEach((code, v) -> {
                try {
                    quota.put(Section.fromCode(code), v);
                } catch (IllegalArgumentException ignored) {
                }
            });
            m.setQuotaJson(writeQuota(quota));
        }
        monthRepository.save(m);
        return snapshot;
    }

    /** 场景3：本周为指定分科增加配额，返回执行前的周快照。 */
    WeekSnapshot applyAccelerateToWeek(Long cycleId, String payload, LocalDate today) {
        Integer addMinutes = extractJsonInt(payload, "addMinutes");
        String sectionCode = extractJsonString(payload, "section");
        if (addMinutes == null || sectionCode == null)
            return null;
        LocalDate weekStart = SlotMigrationPlanner.weekStart(today);
        PlanWeek w = weekRepository.findByCycleIdOrderByWeekIndex(cycleId).stream()
                .filter(ww -> ww.getWeekStart().equals(weekStart))
                .findFirst().orElse(null);
        if (w == null)
            return null;
        WeekSnapshot snapshot = new WeekSnapshot(w.getWeekStart().toString(), w.getQuotaMinutes(), w.getQuotaJson());
        w.setQuotaMinutes(w.getQuotaMinutes() + addMinutes);
        Map<String, Integer> quota = readQuota(w.getQuotaJson());
        quota.put(sectionCode, quota.getOrDefault(sectionCode, 0) + addMinutes);
        Map<Section, Integer> sectionQuota = new LinkedHashMap<>();
        quota.forEach((code, v) -> {
            try {
                sectionQuota.put(Section.fromCode(code), v);
            } catch (IllegalArgumentException ignored) {
            }
        });
        w.setQuotaJson(writeQuota(sectionQuota));
        weekRepository.save(w);
        return snapshot;
    }

    /* ---------------- 自动调整快照（撤销依据） ---------------- */

    /** 撤销用快照：任务时间 + 周配额 + 月配额的执行前镜像。 */
    record Snapshot(List<TaskSnapshot> tasks, List<WeekSnapshot> weeks, List<MonthSnapshot> months) {
    }

    record TaskSnapshot(Long id, String start, String end, String weekStart, String slotType) {
    }

    record WeekSnapshot(String weekStart, Integer quotaMinutes, String quotaJson) {
    }

    record MonthSnapshot(String monthKey, Integer quotaMinutes, String quotaJson) {
    }

    /**
     * 手写快照为 Map/List 公共结构后再交 Jackson 序列化：
     * 快照 record 是本类私有嵌套类型，直接序列化依赖反射访问私有 accessor，显式转 Map 更稳。
     * 失败返回 null（调用方不留无法撤销的 AUTO_EXECUTED 记录）。
     */
    private String writeSnapshot(Snapshot snapshot) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("tasks", snapshot.tasks().stream().map(t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", t.id());
                m.put("start", t.start());
                m.put("end", t.end());
                m.put("weekStart", t.weekStart());
                m.put("slotType", t.slotType());
                return m;
            }).toList());
            root.put("weeks", snapshot.weeks().stream().map(w -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("weekStart", w.weekStart());
                m.put("quotaMinutes", w.quotaMinutes());
                m.put("quotaJson", w.quotaJson());
                return m;
            }).toList());
            root.put("months", snapshot.months().stream().map(mm -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("monthKey", mm.monthKey());
                m.put("quotaMinutes", mm.quotaMinutes());
                m.put("quotaJson", mm.quotaJson());
                return m;
            }).toList());
            return objectMapper.writeValueAsString(root);
        } catch (RuntimeException e) {
            log.error("写入自动调整快照失败", e);
            return null;
        }
    }

    /** 按快照回滚任务时间与周/月配额，并重算受影响周的实际排入量。 */
    private void restoreSnapshot(Long cycleId, String json) {
        if (json == null || json.isBlank())
            return;
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (RuntimeException e) {
            return;
        }
        Set<LocalDate> affectedWeeks = new HashSet<>();
        List<StudyTask> changedTasks = new ArrayList<>();
        for (JsonNode n : root.path("tasks")) {
            StudyTask t = taskRepository.findById(n.path("id").asLong()).orElse(null);
            if (t == null)
                continue;
            if (t.getWeekStart() != null)
                affectedWeeks.add(t.getWeekStart());
            t.setScheduledStart(LocalDateTime.parse(n.path("start").asText()));
            t.setScheduledEnd(LocalDateTime.parse(n.path("end").asText()));
            String weekStart = n.path("weekStart").asText(null);
            LocalDate ws = weekStart == null || weekStart.isBlank() ? null : LocalDate.parse(weekStart);
            t.setWeekStart(ws);
            if (ws != null)
                affectedWeeks.add(ws);
            String slotType = n.path("slotType").asText(null);
            t.setSlotType(slotType == null || slotType.isBlank() ? null : slotType);
            t.setMonthKey(t.getScheduledStart().toString().substring(0, 7));
            changedTasks.add(t);
        }
        taskRepository.saveAll(changedTasks);

        List<PlanWeek> weeks = weekRepository.findByCycleIdOrderByWeekIndex(cycleId);
        List<PlanWeek> changedWeeks = new ArrayList<>();
        for (JsonNode n : root.path("weeks")) {
            String weekStart = n.path("weekStart").asText(null);
            if (weekStart == null)
                continue;
            LocalDate ws = LocalDate.parse(weekStart);
            weeks.stream().filter(w -> w.getWeekStart().equals(ws)).findFirst().ifPresent(w -> {
                if (n.has("quotaMinutes") && !n.path("quotaMinutes").isNull()) {
                    w.setQuotaMinutes(n.path("quotaMinutes").asInt());
                }
                String quotaJson = n.path("quotaJson").asText(null);
                if (quotaJson != null)
                    w.setQuotaJson(quotaJson);
                changedWeeks.add(w);
            });
        }
        weekRepository.saveAll(changedWeeks);

        List<PlanMonth> months = monthRepository.findByCycleIdOrderByMonthIndex(cycleId);
        List<PlanMonth> changedMonths = new ArrayList<>();
        for (JsonNode n : root.path("months")) {
            String monthKey = n.path("monthKey").asText(null);
            if (monthKey == null)
                continue;
            months.stream().filter(m -> m.getMonthKey().equals(monthKey)).findFirst().ifPresent(m -> {
                if (n.has("quotaMinutes") && !n.path("quotaMinutes").isNull()) {
                    m.setQuotaMinutes(n.path("quotaMinutes").asInt());
                }
                String quotaJson = n.path("quotaJson").asText(null);
                if (quotaJson != null)
                    m.setQuotaJson(quotaJson);
                changedMonths.add(m);
            });
        }
        monthRepository.saveAll(changedMonths);

        affectedWeeks.forEach(w -> recomputeWeekAllocated(cycleId, w));
    }

    /* ---------------- JSON 解析辅助（避免引入 Jackson） ---------------- */

    private static String extractJsonString(String json, String key) {
        if (json == null)
            return null;
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0)
            return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }

    private static Integer extractJsonInt(String json, String key) {
        if (json == null)
            return null;
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0)
            return null;
        start += pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate extractJsonDate(String json, String key) {
        String s = extractJsonString(json, key);
        if (s == null)
            return null;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static List<Long> extractJsonLongArray(String json, String key) {
        if (json == null)
            return List.of();
        String pattern = "\"" + key + "\":[";
        int start = json.indexOf(pattern);
        if (start < 0)
            return List.of();
        start += pattern.length();
        int end = json.indexOf("]", start);
        if (end < 0)
            return List.of();
        String content = json.substring(start, end);
        List<Long> result = new ArrayList<>();
        for (String s : content.split(",")) {
            try {
                result.add(Long.parseLong(s.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private static Map<String, Integer> extractJsonIntMap(String json, String key) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (json == null)
            return result;
        String pattern = "\"" + key + "\":{";
        int start = json.indexOf(pattern);
        if (start < 0)
            return result;
        start += pattern.length();
        int end = json.indexOf("}", start);
        if (end < 0)
            return result;
        String content = json.substring(start, end);
        for (String pair : content.split(",")) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                try {
                    result.put(kv[0].replace("\"", "").trim(), Integer.parseInt(kv[1].trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    /* ---------------- 内部 ---------------- */

    private void recomputeWeekAllocated(Long cycleId, LocalDate weekStart) {
        List<StudyTask> weekTasks = taskRepository.findByCycleIdOrderByScheduledStart(cycleId).stream()
                .filter(t -> weekStart.equals(t.getWeekStart()))
                .toList();
        weekRepository.findByCycleIdOrderByWeekIndex(cycleId).stream()
                .filter(w -> w.getWeekStart().equals(weekStart))
                .findFirst()
                .ifPresent(w -> {
                    w.setAllocatedMinutes(weekTasks.stream().mapToInt(StudyTask::getDurationMinutes).sum());
                    weekRepository.save(w);
                });
    }

    private static LocalDateTime parseDateTime(StudyTask task, String hhmmOrFull) {
        try {
            if (hhmmOrFull.length() == 5) {
                return LocalDateTime.of(task.getScheduledStart().toLocalDate(), LocalTime.parse(hhmmOrFull));
            }
            return LocalDateTime.parse(hhmmOrFull);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式错误，应为 HH:MM：" + hhmmOrFull);
        }
    }

    /** 配额 JSON 固定按 L/R/W/S 顺序手写，避免引入额外 JSON 依赖。 */
    private static String writeQuota(Map<Section, Integer> quota) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Section s : Section.values()) {
            if (!first)
                sb.append(',');
            sb.append('"').append(s.getCode()).append("\":").append(quota.getOrDefault(s, 0));
            first = false;
        }
        return sb.append('}').toString();
    }

    private static Map<String, Integer> readQuota(String json) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return m;
        }
        for (String part : json.replace("{", "").replace("}", "").split(",")) {
            String[] kv = part.split(":");
            if (kv.length == 2) {
                try {
                    m.put(kv[0].replace("\"", "").trim(), Integer.parseInt(kv[1].trim()));
                } catch (NumberFormatException ignored) {
                    // 跳过损坏片段
                }
            }
        }
        return m;
    }

    private static List<Section> resolveWeak(List<String> raw, Map<String, Double> current,
            Map<String, Double> target) {
        List<Section> weak = new ArrayList<>();
        if (raw != null) {
            for (String code : raw) {
                try {
                    weak.add(Section.fromCode(code));
                } catch (IllegalArgumentException ignored) {
                    // 忽略非法分科
                }
            }
        }
        if (weak.isEmpty()) {
            current.keySet().stream()
                    .map(code -> Map.entry(code, target.getOrDefault(code, 0.0) - current.getOrDefault(code, 0.0)))
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .filter(e -> e.getValue() > 0)
                    .limit(2)
                    .forEach(e -> weak.add(Section.fromCode(e.getKey())));
        }
        return weak;
    }

    private static String stageLabel(String stage) {
        return switch (stage) {
            case "FOUNDATION" -> "基础期";
            case "INTENSIVE" -> "强化期";
            case "SPRINT" -> "冲刺期";
            default -> stage;
        };
    }

    /* ---------------- 视图组装（兼容引擎中间结构与持久化实体两种输入） ---------------- */

    private CycleView toViewFromPlan(PlanCycle cycle,
            List<CyclePlanner.MonthPlan> months,
            List<CyclePlanner.WeekPlan> weeks,
            List<StudyTask> tasks,
            List<String> warnings) {
        List<CycleView.MonthView> monthViews = months.stream()
                .map(m -> new CycleView.MonthView(
                        m.monthKey(), m.monthIndex(), monthLabel(m.monthKey()),
                        m.start().toString(), m.end().toString(),
                        m.stage(), stageLabel(m.stage()),
                        (int) m.capacity().available(), (int) m.capacity().deep(),
                        (int) m.capacity().free(), (int) m.capacity().fragment(),
                        m.quotaMinutes(), toCodeMap(m.quota())))
                .toList();
        List<CycleView.WeekView> weekViews = weeks.stream()
                .map(w -> new CycleView.WeekView(
                        w.monthKey(), w.weekIndex(), w.weekStart().toString(), w.weekEnd().toString(),
                        w.availableMinutes(), w.quotaMinutes(), w.allocatedMinutes(), toCodeMap(w.quota())))
                .toList();
        return assemble(cycle, monthViews, weekViews, tasks, warnings, List.of());
    }

    private CycleView toViewFromEntities(PlanCycle cycle, List<PlanMonth> months, List<PlanWeek> weeks,
            List<StudyTask> tasks, List<String> warnings) {
        return toViewFromEntities(cycle, months, weeks, tasks, warnings, List.of());
    }

    private CycleView toViewFromEntities(PlanCycle cycle, List<PlanMonth> months, List<PlanWeek> weeks,
            List<StudyTask> tasks, List<String> warnings,
            List<PlanAdjustment> pendingAdjustments) {
        List<CycleView.MonthView> monthViews = months.stream()
                .map(m -> new CycleView.MonthView(
                        m.getMonthKey(), m.getMonthIndex(), monthLabel(m.getMonthKey()),
                        m.getStartDate().toString(), m.getEndDate().toString(),
                        m.getStage(), stageLabel(m.getStage()),
                        m.getAvailableMinutes(), m.getDeepMinutes(),
                        Math.max(0, m.getAvailableMinutes() - m.getDeepMinutes() - m.getFragmentMinutes()),
                        m.getFragmentMinutes(), m.getQuotaMinutes(), readQuota(m.getQuotaJson())))
                .toList();
        List<CycleView.WeekView> weekViews = weeks.stream()
                .map(w -> new CycleView.WeekView(
                        w.getMonthKey(), w.getWeekIndex(),
                        w.getWeekStart().toString(), w.getWeekEnd().toString(),
                        w.getAvailableMinutes(), w.getQuotaMinutes(), w.getAllocatedMinutes(),
                        readQuota(w.getQuotaJson())))
                .toList();
        return assemble(cycle, monthViews, weekViews, tasks, warnings, pendingAdjustments);
    }

    private CycleView assemble(PlanCycle cycle, List<CycleView.MonthView> months,
            List<CycleView.WeekView> weeks, List<StudyTask> tasks,
            List<String> warnings, List<PlanAdjustment> pendingAdjustments) {
        List<StudyTask> sorted = tasks.stream()
                .sorted(Comparator.comparing(StudyTask::getScheduledStart))
                .toList();
        int done = (int) sorted.stream().filter(StudyTask::isDone).count();
        List<CycleView.TaskView> taskViews = sorted.stream()
                .map(t -> new CycleView.TaskView(
                        t.getId(), t.getSection().getCode(), t.getTitle(),
                        t.getObjective(),
                        t.getScheduledStart().toString(), t.getScheduledEnd().toString(),
                        t.getDurationMinutes(), t.getStage(), t.getTaskType(), t.isDone(),
                        t.getMonthKey(),
                        t.getWeekStart() != null ? t.getWeekStart().toString() : null,
                        t.getSlotType(), t.getDifficulty(),
                        t.getProgressStatus() != null ? t.getProgressStatus() : "TODO",
                        t.getActualMinutes()))
                .toList();

        // 计算进度指标
        LocalDate today = LocalDate.now();
        String monthKey = today.toString().substring(0, 7);
        int monthQuota = months.stream()
                .filter(m -> monthKey.equals(m.monthKey()))
                .mapToInt(CycleView.MonthView::quotaMinutes)
                .findFirst()
                .orElse(0);
        ProgressAdjuster.Metrics metrics = ProgressAdjuster.computeMetrics(sorted, monthQuota, today);
        CycleView.Metrics metricsView = new CycleView.Metrics(
                metrics.todayPlanned(), metrics.todayDone(), metrics.todayRate(),
                metrics.recentAvgRate(),
                metrics.monthDone(), metrics.monthQuota(), metrics.monthRate(),
                metrics.overdueTaskCount(), metrics.overdueMinutes());

        // 组装调整建议视图
        List<CycleView.AdjustmentView> adjustmentViews = pendingAdjustments.stream()
                .map(a -> new CycleView.AdjustmentView(
                        a.getId(), a.getScenario(), a.getTitle(), a.getDescription(),
                        a.getStatus(), a.getTriggerRate(),
                        a.getTriggerDate() != null ? a.getTriggerDate().toString() : null,
                        a.getSource() != null ? a.getSource() : "MANUAL"))
                .toList();

        return new CycleView(
                new CycleView.Cycle(cycle.getId(), cycle.getExamType(),
                        cycle.getStartDate().toString(), cycle.getEndDate().toString(),
                        cycle.getTargetTotal(), cycle.getCurrentTotal(), cycle.getStatus(),
                        normalizeLevel(cycle.getAutoAdjustLevel())),
                months, weeks, warnings, taskViews.size(), done, taskViews,
                metricsView, adjustmentViews);
    }

    private static Map<String, Integer> toCodeMap(Map<Section, Integer> quota) {
        Map<String, Integer> m = new LinkedHashMap<>();
        quota.forEach((s, v) -> m.put(s.getCode(), v));
        return m;
    }

    private static String monthLabel(String monthKey) {
        String[] parts = monthKey.split("-");
        return parts[0] + "年" + Integer.parseInt(parts[1]) + "月";
    }
}
