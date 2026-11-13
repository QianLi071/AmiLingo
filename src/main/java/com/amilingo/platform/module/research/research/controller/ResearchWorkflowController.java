package com.amilingo.platform.module.research.controller;

import com.amilingo.platform.common.dto.Result;
import com.amilingo.platform.module.research.dto.ResearchWorkflowDTO.*;
import com.amilingo.platform.module.research.entity.*;
import com.amilingo.platform.module.research.service.ResearchWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 科研项目工作流接口；actorUserId 后续可由主框架统一替换为 JWT 当前用户。 */
@RestController
@RequestMapping("/api/research")
@RequiredArgsConstructor
public class ResearchWorkflowController {
    private final ResearchWorkflowService service;

    @PostMapping("/{projectId}/submit")
    public Result<ResearchProject> submit(@PathVariable Long projectId, @Valid @RequestBody SubmitProjectRequest body) {
        return Result.ok(service.submitProject(projectId, body));
    }
    @PostMapping("/{projectId}/review")
    public Result<ResearchProject> review(@PathVariable Long projectId, @Valid @RequestBody ReviewProjectRequest body) {
        return Result.ok(service.reviewProject(projectId, body));
    }

    @PostMapping("/{projectId}/join-applications")
    public Result<ProjectJoinApplication> apply(@PathVariable Long projectId, @Valid @RequestBody JoinApplicationRequest body) {
        return Result.ok(service.applyToJoin(projectId, body));
    }
    @GetMapping("/{projectId}/join-applications")
    public Result<List<ProjectJoinApplication>> joinApplications(@PathVariable Long projectId, @RequestParam Long actorUserId) {
        return Result.ok(service.listJoinApplications(projectId, actorUserId));
    }
    @GetMapping("/join-applications/mine")
    public Result<List<ProjectJoinApplication>> myApplications(@RequestParam Long userId) {
        return Result.ok(service.listMyApplications(userId));
    }
    @PostMapping("/join-applications/{applicationId}/review")
    public Result<ProjectJoinApplication> reviewJoin(@PathVariable Long applicationId, @Valid @RequestBody ReviewJoinRequest body) {
        return Result.ok(service.reviewJoin(applicationId, body));
    }
    @PostMapping("/join-applications/{applicationId}/withdraw")
    public Result<ProjectJoinApplication> withdrawJoin(@PathVariable Long applicationId, @RequestParam Long applicantUserId) {
        return Result.ok(service.withdrawJoin(applicationId, applicantUserId));
    }

    @GetMapping("/{projectId}/members")
    public Result<List<ProjectMember>> members(@PathVariable Long projectId, @RequestParam Long actorUserId) {
        return Result.ok(service.listMembers(projectId, actorUserId));
    }
    @PostMapping("/{projectId}/members")
    public Result<ProjectMember> addMember(@PathVariable Long projectId, @Valid @RequestBody MemberRequest body) {
        return Result.ok(service.addMember(projectId, body));
    }
    @PutMapping("/members/{memberId}")
    public Result<ProjectMember> updateMember(@PathVariable Long memberId, @Valid @RequestBody UpdateMemberRequest body) {
        return Result.ok(service.updateMember(memberId, body));
    }
    @DeleteMapping("/members/{memberId}")
    public Result<Void> removeMember(@PathVariable Long memberId, @RequestParam Long actorUserId) {
        service.removeMember(memberId, actorUserId); return Result.ok();
    }

    @GetMapping("/{projectId}/milestones")
    public Result<List<ProjectMilestone>> milestones(@PathVariable Long projectId, @RequestParam Long actorUserId) {
        return Result.ok(service.listMilestones(projectId, actorUserId));
    }
    @PostMapping("/{projectId}/milestones")
    public Result<ProjectMilestone> createMilestone(@PathVariable Long projectId, @Valid @RequestBody MilestoneRequest body) {
        return Result.ok(service.createMilestone(projectId, body));
    }
    @PutMapping("/milestones/{milestoneId}")
    public Result<ProjectMilestone> updateMilestone(@PathVariable Long milestoneId, @Valid @RequestBody UpdateMilestoneRequest body) {
        return Result.ok(service.updateMilestone(milestoneId, body));
    }
    @DeleteMapping("/milestones/{milestoneId}")
    public Result<Void> deleteMilestone(@PathVariable Long milestoneId, @RequestParam Long actorUserId) {
        service.deleteMilestone(milestoneId, actorUserId); return Result.ok();
    }

    @GetMapping("/{projectId}/tasks")
    public Result<List<ProjectTask>> tasks(@PathVariable Long projectId, @RequestParam Long actorUserId) {
        return Result.ok(service.listTasks(projectId, actorUserId));
    }
    @GetMapping("/tasks/mine")
    public Result<List<ProjectTask>> myTasks(@RequestParam Long userId) {
        return Result.ok(service.listAssignedTasks(userId));
    }
    @PostMapping("/{projectId}/tasks")
    public Result<ProjectTask> createTask(@PathVariable Long projectId, @Valid @RequestBody TaskRequest body) {
        return Result.ok(service.createTask(projectId, body));
    }
    @PutMapping("/tasks/{taskId}")
    public Result<ProjectTask> updateTask(@PathVariable Long taskId, @Valid @RequestBody UpdateTaskRequest body) {
        return Result.ok(service.updateTask(taskId, body));
    }
    @PostMapping("/tasks/{taskId}/progress")
    public Result<ProjectTask> updateTaskProgress(@PathVariable Long taskId, @Valid @RequestBody TaskProgressRequest body) {
        return Result.ok(service.updateTaskProgress(taskId, body));
    }
    @GetMapping("/tasks/{taskId}/progress-history")
    public Result<List<TaskProgressLog>> progressHistory(@PathVariable Long taskId, @RequestParam Long actorUserId) {
        return Result.ok(service.taskProgressHistory(taskId, actorUserId));
    }
    @DeleteMapping("/tasks/{taskId}")
    public Result<Void> deleteTask(@PathVariable Long taskId, @RequestParam Long actorUserId) {
        service.deleteTask(taskId, actorUserId); return Result.ok();
    }

    @GetMapping("/{projectId}/advisor-records")
    public Result<List<AdvisorRecord>> advisorRecords(@PathVariable Long projectId, @RequestParam Long actorUserId) {
        return Result.ok(service.listAdvisorRecords(projectId, actorUserId));
    }
    @PostMapping("/{projectId}/advisor-records")
    public Result<AdvisorRecord> createAdvisorRecord(@PathVariable Long projectId, @Valid @RequestBody AdvisorRecordRequest body) {
        return Result.ok(service.createAdvisorRecord(projectId, body));
    }
    @PutMapping("/advisor-records/{recordId}")
    public Result<AdvisorRecord> updateAdvisorRecord(@PathVariable Long recordId, @Valid @RequestBody AdvisorRecordRequest body) {
        return Result.ok(service.updateAdvisorRecord(recordId, body));
    }
    @DeleteMapping("/advisor-records/{recordId}")
    public Result<Void> deleteAdvisorRecord(@PathVariable Long recordId, @RequestParam Long actorUserId) {
        service.deleteAdvisorRecord(recordId, actorUserId); return Result.ok();
    }

    @GetMapping("/{projectId}/deliverables")
    public Result<List<ProjectDeliverable>> deliverables(@PathVariable Long projectId, @RequestParam Long actorUserId,
                                                          @RequestParam(required = false) String type) {
        return Result.ok(service.listDeliverables(projectId, actorUserId, type));
    }
    @PostMapping("/{projectId}/deliverables")
    public Result<ProjectDeliverable> createDeliverable(@PathVariable Long projectId, @Valid @RequestBody DeliverableRequest body) {
        return Result.ok(service.createDeliverable(projectId, body));
    }
    @PutMapping("/deliverables/{deliverableId}")
    public Result<ProjectDeliverable> updateDeliverable(@PathVariable Long deliverableId, @Valid @RequestBody DeliverableRequest body) {
        return Result.ok(service.updateDeliverable(deliverableId, body));
    }
    @PostMapping("/deliverables/{deliverableId}/link-patent")
    public Result<ProjectDeliverable> linkPatent(@PathVariable Long deliverableId, @Valid @RequestBody LinkPatentRequest body) {
        return Result.ok(service.linkPatent(deliverableId, body));
    }
    @DeleteMapping("/deliverables/{deliverableId}")
    public Result<Void> deleteDeliverable(@PathVariable Long deliverableId, @RequestParam Long actorUserId) {
        service.deleteDeliverable(deliverableId, actorUserId); return Result.ok();
    }
}
