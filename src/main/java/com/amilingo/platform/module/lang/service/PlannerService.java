package com.amilingo.platform.module.lang.service;

import com.amilingo.platform.module.lang.ai.PlanNarrator;
import com.amilingo.platform.module.lang.dto.request.CyclePlanRequest;
import com.amilingo.platform.module.lang.dto.request.PlannerRequest;
import com.amilingo.platform.module.lang.dto.request.TaskProgressRequest;
import com.amilingo.platform.module.lang.dto.response.CycleView;
import com.amilingo.platform.module.lang.dto.response.PlanView;
import com.amilingo.platform.module.lang.entity.ExamGoal;
import com.amilingo.platform.module.lang.entity.ScheduleBlock;
import com.amilingo.platform.module.lang.enums.ExamType;
import com.amilingo.platform.module.lang.enums.Section;
import com.amilingo.platform.module.lang.planner.PlanInput;
import com.amilingo.platform.module.lang.planner.StageModel;
import com.amilingo.platform.module.lang.planner.StudyTask;
import com.amilingo.platform.module.lang.repository.ExamGoalRepository;
import com.amilingo.platform.module.lang.repository.ScheduleBlockRepository;
import com.amilingo.platform.module.lang.repository.StudyTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 旧版排程接口的兼容适配层。
 *
 * <p>URL 契约（/planner/generate、/planner/current、/planner/tasks/{id}/toggle）与
 * {@link PlanView} 结构保持不变，前端零改动；内部全部委托长周期规划引擎
 * {@link CyclePlannerService}，旧的 PlanGenerator 引擎不再被调用：
 * <ul>
 *   <li>generate：生成草稿周期后立即采纳（旧语义为「生成即生效」，跳过草稿确认流）。</li>
 *   <li>current：读最新已采纳周期；无周期时返回 null（前端检测到无计划会自动生成）。</li>
 *   <li>toggle：映射为进度回调 DONE/TODO（同时触发调整建议评估）。</li>
 * </ul>
 */
@Service
public class PlannerService {

    /** 兜底作息雕刻基准窗，必须与 SlotExtractor.DEFAULT_WINDOW_START/END 一致。 */
    private static final LocalTime WINDOW_START = LocalTime.of(7, 0);
    private static final LocalTime WINDOW_END = LocalTime.of(23, 30);
    /** 兜底作息每日学习窗分钟数上下限（短于 30 分钟无法成槽，上限对齐 22:30/12:30 收工）。 */
    private static final long DEFAULT_WINDOW_MIN_MINUTES = 30;
    private static final long DEFAULT_WINDOW_MAX_MINUTES = 210;

    private final CyclePlannerService cyclePlannerService;
    private final ScheduleBlockRepository scheduleBlockRepository;
    private final StudyTaskRepository studyTaskRepository;
    private final ExamGoalRepository examGoalRepository;
    private final PlanNarrator planNarrator;
    private final StageModel stageModel;

    public PlannerService(CyclePlannerService cyclePlannerService,
                          ScheduleBlockRepository scheduleBlockRepository,
                          StudyTaskRepository studyTaskRepository,
                          ExamGoalRepository examGoalRepository,
                          PlanNarrator planNarrator,
                          StageModel stageModel) {
        this.cyclePlannerService = cyclePlannerService;
        this.scheduleBlockRepository = scheduleBlockRepository;
        this.studyTaskRepository = studyTaskRepository;
        this.examGoalRepository = examGoalRepository;
        this.planNarrator = planNarrator;
        this.stageModel = stageModel;
    }

    @Transactional
    public PlanView generate(Long userId, PlannerRequest request) {
        ExamGoal storedGoal = examGoalRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).orElse(null);

        ExamType examType = request.examType() != null
                ? ExamType.fromCode(request.examType())
                : (storedGoal != null ? storedGoal.getExamType() : ExamType.IELTS_A);

        LocalDate examDate = request.examDate() != null && !request.examDate().isBlank()
                ? LocalDate.parse(request.examDate())
                : (storedGoal != null ? storedGoal.getExamDate() : LocalDate.now().plusDays(90));

        Map<String, Double> current = defaultIfNull(request.current());
        Map<String, Double> target = request.target() != null
                ? new LinkedHashMap<>(request.target())
                : defaultTarget();

        Double targetTotal = request.targetTotal() != null
                ? request.targetTotal()
                : (storedGoal != null ? storedGoal.getTargetTotal() : averageOf(target));

        List<Section> weak = resolveWeak(request.weak(), current, target);
        double weeklyHours = request.weeklyHours() != null ? request.weeklyHours() : 10.0;
        int vocabulary = request.vocabulary() != null ? request.vocabulary() : 5000;

        // 兜底（策略A）：仅当该用户一条时间资源都没有时才触发——主路径完全无法闭合。
        // 在同一事务内插入周循环 UNAVAILABLE 块雕刻默认作息，生成结束后立即删除，
        // 异常时事务回滚自动清除，绝不污染用户真实时间资源。
        boolean syntheticSchedule = scheduleBlockRepository.findByUserId(userId).isEmpty();
        List<ScheduleBlock> syntheticBlocks = List.of();
        if (syntheticSchedule) {
            syntheticBlocks = buildDefaultSchedule(userId, weeklyHours);
            scheduleBlockRepository.saveAll(syntheticBlocks);
        }

        CyclePlanRequest cycleRequest = new CyclePlanRequest(
                examType.name(),
                LocalDate.now().toString(),
                examDate.toString(),
                current,
                target,
                targetTotal,
                weak.stream().map(Section::getCode).toList());

        // 委托长周期引擎：先生成草稿，再立即采纳（对齐旧版「生成即生效」语义）。
        CycleView generated = cyclePlannerService.generate(userId, cycleRequest);
        CycleView accepted = cyclePlannerService.accept(userId, generated.cycle().id());

        if (!syntheticBlocks.isEmpty()) {
            scheduleBlockRepository.deleteAllInBatch(syntheticBlocks);
        }

        // AI 本周目标文案沿用 PlanNarrator（旧引擎保留下来的唯一回灌能力）。
        String weeklyGoal = safeNarrate(userId, examType, examDate, current, target,
                targetTotal, weak, weeklyHours, vocabulary, accepted.cycle().id());

        List<String> warnings = new ArrayList<>();
        if (syntheticSchedule) {
            warnings.add(defaultScheduleHint(weeklyHours));
        }
        warnings.addAll(generated.warnings());

        return toPlanView(accepted, weeklyGoal, warnings);
    }

    @Transactional(readOnly = true)
    public PlanView getCurrentPlan(Long userId) {
        CycleView view = cyclePlannerService.getCurrent(userId);
        return view == null ? null : toPlanView(view, "", List.of());
    }

    @Transactional
    public PlanView toggleTask(Long userId, Long taskId) {
        StudyTask task = studyTaskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        if (task.getCycleId() != null) {
            // 新引擎任务：勾选映射为进度回调，同时触发调整建议评估。
            String nextStatus = task.isDone() ? "TODO" : "DONE";
            CycleView view = cyclePlannerService.reportProgress(
                    userId, taskId, new TaskProgressRequest(nextStatus, null));
            return toPlanView(view, "", List.of());
        }
        // 尚未重建计划的旧引擎遗留任务：保持旧的直接翻转语义，视图沿用 legacy 任务集合。
        task.setDone(!task.isDone());
        studyTaskRepository.save(task);
        return legacyView(userId);
    }

    // ------------------------------------------------------------------

    /**
     * 策略A 兜底作息：按 weeklyHours 均摊到每天一个学习窗
     * （工作日 19:00 起、周末 09:00 起），在默认空闲窗内用 UNAVAILABLE 块反向雕刻。
     */
    private static List<ScheduleBlock> buildDefaultSchedule(Long userId, double weeklyHours) {
        long minutesPerDay = Math.round(weeklyHours * 60.0 / 7.0);
        minutesPerDay = Math.max(DEFAULT_WINDOW_MIN_MINUTES,
                Math.min(DEFAULT_WINDOW_MAX_MINUTES, minutesPerDay));
        List<ScheduleBlock> blocks = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            LocalTime studyStart = day <= 5 ? LocalTime.of(19, 0) : LocalTime.of(9, 0);
            LocalTime studyEnd = studyStart.plusMinutes(minutesPerDay);
            if (studyStart.isAfter(WINDOW_START)) {
                blocks.add(unavailableBlock(userId, day, WINDOW_START, studyStart));
            }
            if (studyEnd.isBefore(WINDOW_END)) {
                blocks.add(unavailableBlock(userId, day, studyEnd, WINDOW_END));
            }
        }
        return blocks;
    }

    private static ScheduleBlock unavailableBlock(Long userId, int day, LocalTime start, LocalTime end) {
        return ScheduleBlock.builder()
                .userId(userId)
                .dayOfWeek(day)
                .startTime(start)
                .endTime(end)
                .blockType(ScheduleBlock.BlockType.UNAVAILABLE)
                .title("系统默认作息（可在时间资源修改）")
                .recurrence(ScheduleBlock.RecurrenceType.WEEKLY)
                .build();
    }

    private static String defaultScheduleHint(double weeklyHours) {
        long minutesPerDay = Math.max(DEFAULT_WINDOW_MIN_MINUTES,
                Math.min(DEFAULT_WINDOW_MAX_MINUTES, Math.round(weeklyHours * 60.0 / 7.0)));
        return String.format(
                "你尚未设置时间资源，已按每周约 %.0f 小时的默认作息排程"
                + "（工作日晚间、周末上午各约 %d 分钟），可在「时间资源」自定义后重新生成。",
                weeklyHours, minutesPerDay);
    }

    private String safeNarrate(Long userId, ExamType examType, LocalDate examDate,
                               Map<String, Double> current, Map<String, Double> target,
                               Double targetTotal, List<Section> weak,
                               double weeklyHours, int vocabulary, Long cycleId) {
        List<StudyTask> allTasks = studyTaskRepository.findByCycleIdOrderByScheduledStart(cycleId);
        LocalDate today = LocalDate.now();
        PlanInput input = new PlanInput(userId, examType, examDate, today,
                current, target, targetTotal, weak, weeklyHours, vocabulary);
        try {
            String goal = planNarrator.narrate(input, stageModel.stageFor(today, examDate), allTasks);
            if (goal != null && !goal.isBlank()) {
                return goal.trim();
            }
        } catch (RuntimeException ignored) {
            // AI 不可用时使用确定性兜底，不阻塞生成
        }
        return "本周进入" + stageModel.stageFor(today, examDate).getDisplayName()
                + "，请按计划坚持完成每日任务，重点突破薄弱分科，保持节奏稳步提分。";
    }

    /** 旧引擎遗留任务的视图（仅服务于尚未重建计划的账号的一次勾选）。 */
    private PlanView legacyView(Long userId) {
        List<StudyTask> tasks = studyTaskRepository.findLegacyByUserId(userId);
        if (tasks.isEmpty()) {
            return null;
        }
        long latestPlan = tasks.stream().mapToLong(StudyTask::getPlanId).max().orElseThrow();
        List<StudyTask> planTasks = tasks.stream()
                .filter(t -> t.getPlanId() == latestPlan)
                .sorted(Comparator.comparing(StudyTask::getScheduledStart))
                .toList();
        return new PlanView(latestPlan, "", List.of(), planTasks.size(),
                planTasks.stream().map(PlannerService::toLegacyTaskView).toList());
    }

    private static PlanView toPlanView(CycleView view, String weeklyGoal, List<String> warnings) {
        return new PlanView(
                view.cycle().id(),
                weeklyGoal,
                warnings,
                view.taskCount(),
                view.tasks().stream()
                        .map(t -> new PlanView.TaskView(
                                t.taskId(),
                                t.section(),
                                t.title(),
                                t.objective(),
                                t.start(),
                                t.end(),
                                t.durationMinutes(),
                                t.stage(),
                                t.taskType(),
                                t.done()))
                        .toList());
    }

    private static PlanView.TaskView toLegacyTaskView(StudyTask task) {
        return new PlanView.TaskView(
                task.getId(),
                task.getSection().getCode(),
                task.getTitle(),
                task.getDescription(),
                task.getScheduledStart().toString(),
                task.getScheduledEnd().toString(),
                task.getDurationMinutes(),
                task.getStage(),
                task.getTaskType(),
                task.isDone());
    }

    // ------------------------------------------------------------------
    private static Map<String, Double> defaultIfNull(Map<String, Double> source) {
        if (source != null) {
            return new LinkedHashMap<>(source);
        }
        return new LinkedHashMap<>(Map.of("L", 5.0, "R", 5.5, "W", 5.0, "S", 5.0));
    }

    private static Map<String, Double> defaultTarget() {
        return new LinkedHashMap<>(Map.of("L", 6.5, "R", 6.5, "W", 6.0, "S", 6.0));
    }

    private static Double averageOf(Map<String, Double> scores) {
        return Math.round(scores.values().stream().mapToDouble(Double::doubleValue)
                .average().orElse(6.5) * 10) / 10.0;
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
            // 按差距从大到小推导
            current.keySet().stream()
                    .map(code -> Map.entry(code,
                            target.getOrDefault(code, 0.0) - current.getOrDefault(code, 0.0)))
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .filter(e -> e.getValue() > 0)
                    .limit(2)
                    .forEach(e -> weak.add(Section.fromCode(e.getKey())));
        }
        return weak;
    }
}
