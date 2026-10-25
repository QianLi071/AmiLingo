package com.amilingo.platform.module.lang.controller;

import com.amilingo.platform.common.config.security.SecurityUtil;
import com.amilingo.platform.common.dto.ApiResponse;
import com.amilingo.platform.module.lang.dto.request.AutoAdjustLevelRequest;
import com.amilingo.platform.module.lang.dto.request.CyclePlanRequest;
import com.amilingo.platform.module.lang.dto.request.TaskProgressRequest;
import com.amilingo.platform.module.lang.dto.request.TaskUpdateRequest;
import com.amilingo.platform.module.lang.dto.response.CycleView;
import com.amilingo.platform.module.lang.dto.response.TaskPoolView;
import com.amilingo.platform.module.lang.service.CyclePlannerService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 长周期规划引擎接口：月-周规划、AI 时间切片任务分配、采纳/拒绝、手动修改、任务模块对接桩。
 */
@RestController
@RequestMapping("/api/v1/planner")
public class CyclePlannerController {

    private final CyclePlannerService cyclePlannerService;

    public CyclePlannerController(CyclePlannerService cyclePlannerService) {
        this.cyclePlannerService = cyclePlannerService;
    }

    /** 生成草稿周期（不删除前先在服务端覆盖旧草稿；返回完整月/周/任务结构）。 */
    @PostMapping("/cycle/generate")
    public ApiResponse<CycleView> generate(@RequestBody CyclePlanRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("月度规划草稿已生成，请确认采纳", cyclePlannerService.generate(userId, request));
    }

    @GetMapping("/cycle/current")
    public ApiResponse<CycleView> current() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok(cyclePlannerService.getCurrent(userId));
    }

    /** 一键采纳：草稿生效。 */
    @PostMapping("/cycle/{cycleId}/accept")
    public ApiResponse<CycleView> accept(@PathVariable Long cycleId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("已采纳该备考规划", cyclePlannerService.accept(userId, cycleId));
    }

    /** 全部拒绝：删除草稿。 */
    @PostMapping("/cycle/{cycleId}/reject")
    public ApiResponse<CycleView> reject(@PathVariable Long cycleId) {
        Long userId = SecurityUtil.getCurrentUserId();
        cyclePlannerService.reject(userId, cycleId);
        return ApiResponse.ok("已拒绝该规划草稿", null);
    }

    /** 局部修改：调整切片任务的标题/目标/起止时间。 */
    @PutMapping("/tasks/{taskId}")
    public ApiResponse<CycleView> updateTask(@PathVariable Long taskId,
                                             @RequestBody TaskUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("任务已修改", cyclePlannerService.updateTask(userId, taskId, request));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<CycleView> deleteTask(@PathVariable Long taskId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("任务已删除", cyclePlannerService.deleteTask(userId, taskId));
    }

    /** 任务模块进度回调（接口约定①桩）。 */
    @PostMapping("/tasks/{taskId}/progress")
    public ApiResponse<CycleView> reportProgress(@PathVariable Long taskId,
                                                 @RequestBody TaskProgressRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("进度已同步", cyclePlannerService.reportProgress(userId, taskId, request));
    }

    /** 任务基础信息读取（接口约定②桩）：任务池类型/工作量/难度标签。 */
    @GetMapping("/task-pool")
    public ApiResponse<TaskPoolView> taskPool() {
        return ApiResponse.ok(cyclePlannerService.taskPool());
    }

    /* ---------------- 进度驱动的调整建议（第三节） ---------------- */

    /** 查询当前周期的待处理调整建议。 */
    @GetMapping("/adjustments/pending")
    public ApiResponse<List<CycleView.AdjustmentView>> pendingAdjustments() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<CycleView.AdjustmentView> views = cyclePlannerService.pendingAdjustments(userId).stream()
                .map(a -> new CycleView.AdjustmentView(
                        a.getId(), a.getScenario(), a.getTitle(), a.getDescription(),
                        a.getStatus(), a.getTriggerRate(),
                        a.getTriggerDate() != null ? a.getTriggerDate().toString() : null,
                        a.getSource() != null ? a.getSource() : "MANUAL"))
                .toList();
        return ApiResponse.ok(views);
    }

    /** 撤销一条系统自动执行的调整（按执行前快照恢复）。 */
    @PostMapping("/adjustments/{adjustmentId}/undo")
    public ApiResponse<CycleView> undoAdjustment(@PathVariable Long adjustmentId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("已撤销本次自动调整", cyclePlannerService.undoAdjustment(userId, adjustmentId));
    }

    /** 查询自动调整级别（OFF/SMART/FULL；尚无周期时为 null）。 */
    @GetMapping("/cycle/auto-adjust-level")
    public ApiResponse<Map<String, String>> getAutoAdjustLevel() {
        Long userId = SecurityUtil.getCurrentUserId();
        Map<String, String> body = new java.util.HashMap<>();
        body.put("level", cyclePlannerService.getAutoAdjustLevel(userId, null));
        return ApiResponse.ok(body);
    }

    /** 设置自动调整级别（OFF 全手动 / SMART 场景1自动（默认）/ FULL 全自动）。 */
    @PutMapping("/cycle/auto-adjust-level")
    public ApiResponse<Map<String, String>> updateAutoAdjustLevel(@RequestBody AutoAdjustLevelRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        String level = cyclePlannerService.updateAutoAdjustLevel(userId, request.cycleId(), request.level());
        return ApiResponse.ok("自动调整级别已更新", Map.of("level", level));
    }

    /** 采纳调整建议：执行对应动作（迁移/下调配额/加速），返回最新规划视图。 */
    @PostMapping("/adjustments/{adjustmentId}/accept")
    public ApiResponse<CycleView> acceptAdjustment(@PathVariable Long adjustmentId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("已采纳调整建议", cyclePlannerService.acceptAdjustment(userId, adjustmentId));
    }

    /** 拒绝调整建议。 */
    @PostMapping("/adjustments/{adjustmentId}/reject")
    public ApiResponse<CycleView> rejectAdjustment(@PathVariable Long adjustmentId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.ok("已拒绝调整建议", cyclePlannerService.rejectAdjustment(userId, adjustmentId));
    }
}
