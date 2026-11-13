package com.amilingo.platform.module.research.service;

import com.amilingo.platform.module.research.dto.ResearchWorkflowDTO.*;
import com.amilingo.platform.module.research.entity.*;
import com.amilingo.platform.module.research.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 科研项目从立项、组队、执行到成果归档的完整业务服务。 */
@Service
@RequiredArgsConstructor
public class ResearchWorkflowService {
    private final ResearchProjectRepository projectRepo;
    private final ProjectMemberRepository memberRepo;
    private final ProjectJoinApplicationRepository joinRepo;
    private final ProjectMilestoneRepository milestoneRepo;
    private final ProjectTaskRepository taskRepo;
    private final TaskProgressLogRepository progressLogRepo;
    private final AdvisorRecordRepository advisorRepo;
    private final ProjectDeliverableRepository deliverableRepo;

    @Transactional
    public ResearchProject submitProject(Long projectId, SubmitProjectRequest request) {
        ResearchProject project = project(projectId);
        requireOwner(project, request.actorUserId());
        requireState(project.getApplicationStatus(), "DRAFT", "REJECTED");
        project.setApplicationStatus("PENDING");
        project.setApprovalOpinion(null);
        project.setApprovedBy(null);
        project.setApprovedAt(null);
        project.setSubmittedAt(LocalDateTime.now());
        return projectRepo.save(project);
    }

    @Transactional
    public ResearchProject reviewProject(Long projectId, ReviewProjectRequest request) {
        ResearchProject project = project(projectId);
        if (project.getUserId().equals(request.reviewerUserId())) {
            throw new IllegalArgumentException("项目创建人不能审批自己的立项申请");
        }
        requireState(project.getApplicationStatus(), "PENDING");
        project.setApplicationStatus(request.decision());
        project.setApprovalOpinion(request.opinion());
        project.setApprovedBy(request.reviewerUserId());
        project.setApprovedAt(LocalDateTime.now());
        return projectRepo.save(project);
    }

    @Transactional
    public ProjectJoinApplication applyToJoin(Long projectId, JoinApplicationRequest request) {
        project(projectId);
        if (isActiveMember(projectId, request.applicantUserId())) {
            throw new IllegalArgumentException("用户已是项目成员");
        }
        if (joinRepo.existsByProjectIdAndApplicantUserIdAndStatus(projectId, request.applicantUserId(), "PENDING")) {
            throw new IllegalArgumentException("已有待审批的加入申请");
        }
        return joinRepo.save(ProjectJoinApplication.builder()
                .projectId(projectId).applicantUserId(request.applicantUserId())
                .message(request.message()).status("PENDING").build());
    }

    @Transactional
    public ProjectJoinApplication reviewJoin(Long applicationId, ReviewJoinRequest request) {
        ProjectJoinApplication application = joinRepo.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("加入申请不存在"));
        requireManager(application.getProjectId(), request.reviewerUserId());
        requireState(application.getStatus(), "PENDING");
        application.setStatus(request.decision());
        application.setReviewOpinion(request.opinion());
        application.setReviewedBy(request.reviewerUserId());
        application.setReviewedAt(LocalDateTime.now());
        if ("APPROVED".equals(request.decision()) && !isActiveMember(application.getProjectId(), application.getApplicantUserId())) {
            memberRepo.save(ProjectMember.builder()
                    .projectId(application.getProjectId())
                    .userId(application.getApplicantUserId())
                    .role(blankDefault(request.memberRole(), "MEMBER"))
                    .status("ACTIVE").build());
        }
        return joinRepo.save(application);
    }

    @Transactional
    public ProjectJoinApplication withdrawJoin(Long applicationId, Long applicantUserId) {
        ProjectJoinApplication application = joinRepo.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("加入申请不存在"));
        if (!application.getApplicantUserId().equals(applicantUserId)) {
            throw new SecurityException("只能撤回自己的加入申请");
        }
        requireState(application.getStatus(), "PENDING");
        application.setStatus("WITHDRAWN");
        return joinRepo.save(application);
    }

    public List<ProjectJoinApplication> listJoinApplications(Long projectId, Long actorUserId) {
        requireManager(projectId, actorUserId);
        return joinRepo.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public List<ProjectJoinApplication> listMyApplications(Long userId) {
        return joinRepo.findByApplicantUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public ProjectMember addMember(Long projectId, MemberRequest request) {
        requireManager(projectId, request.actorUserId());
        if (memberRepo.findByProjectIdAndUserId(projectId, request.userId()).isPresent()) {
            throw new IllegalArgumentException("项目成员已存在");
        }
        return memberRepo.save(ProjectMember.builder().projectId(projectId).userId(request.userId())
                .role(blankDefault(request.role(), "MEMBER")).status("ACTIVE").build());
    }

    @Transactional
    public ProjectMember updateMember(Long memberId, UpdateMemberRequest request) {
        ProjectMember member = memberRepo.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("项目成员不存在"));
        requireManager(member.getProjectId(), request.actorUserId());
        if ("OWNER".equals(member.getRole())) throw new IllegalArgumentException("项目负责人不能被停用或降级");
        if (request.role() != null) member.setRole(request.role());
        if (request.status() != null) member.setStatus(request.status());
        return memberRepo.save(member);
    }

    @Transactional
    public void removeMember(Long memberId, Long actorUserId) {
        ProjectMember member = memberRepo.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("项目成员不存在"));
        requireManager(member.getProjectId(), actorUserId);
        if ("OWNER".equals(member.getRole())) throw new IllegalArgumentException("不能移除项目负责人");
        memberRepo.delete(member);
    }

    public List<ProjectMember> listMembers(Long projectId, Long actorUserId) {
        requireParticipant(projectId, actorUserId);
        return memberRepo.findByProjectIdOrderByCreatedAtAsc(projectId);
    }

    @Transactional
    public ProjectMilestone createMilestone(Long projectId, MilestoneRequest request) {
        requireManager(projectId, request.actorUserId());
        validateDates(request.plannedStartDate(), request.plannedEndDate());
        return milestoneRepo.save(ProjectMilestone.builder().projectId(projectId).name(request.name())
                .description(request.description()).plannedStartDate(request.plannedStartDate())
                .plannedEndDate(request.plannedEndDate()).sortOrder(request.sortOrder())
                .status("PENDING").progress(0).build());
    }

    @Transactional
    public ProjectMilestone updateMilestone(Long milestoneId, UpdateMilestoneRequest request) {
        ProjectMilestone m = milestone(milestoneId);
        requireManager(m.getProjectId(), request.actorUserId());
        LocalDate start = request.plannedStartDate() == null ? m.getPlannedStartDate() : request.plannedStartDate();
        LocalDate end = request.plannedEndDate() == null ? m.getPlannedEndDate() : request.plannedEndDate();
        validateDates(start, end);
        if (request.name() != null) m.setName(request.name());
        if (request.description() != null) m.setDescription(request.description());
        if (request.plannedStartDate() != null) m.setPlannedStartDate(request.plannedStartDate());
        if (request.plannedEndDate() != null) m.setPlannedEndDate(request.plannedEndDate());
        if (request.status() != null) m.setStatus(request.status());
        if (request.progress() != null) m.setProgress(request.progress());
        if (request.sortOrder() != null) m.setSortOrder(request.sortOrder());
        if (m.getProgress() == 100 || "COMPLETED".equals(m.getStatus())) {
            m.setProgress(100); m.setStatus("COMPLETED");
            if (m.getCompletedDate() == null) m.setCompletedDate(LocalDate.now());
        }
        ProjectMilestone saved = milestoneRepo.save(m);
        refreshProjectProgress(m.getProjectId());
        return saved;
    }

    @Transactional
    public void deleteMilestone(Long milestoneId, Long actorUserId) {
        ProjectMilestone m = milestone(milestoneId);
        requireManager(m.getProjectId(), actorUserId);
        if (!taskRepo.findByMilestoneIdOrderByDueDateAscIdAsc(milestoneId).isEmpty()) {
            throw new IllegalArgumentException("里程碑下仍有任务，不能删除");
        }
        milestoneRepo.delete(m);
        refreshProjectProgress(m.getProjectId());
    }

    public List<ProjectMilestone> listMilestones(Long projectId, Long actorUserId) {
        requireParticipant(projectId, actorUserId);
        return milestoneRepo.findByProjectIdOrderBySortOrderAscIdAsc(projectId);
    }

    @Transactional
    public ProjectTask createTask(Long projectId, TaskRequest request) {
        requireManager(projectId, request.actorUserId());
        validateDates(request.plannedStartDate(), request.dueDate());
        validateMilestone(projectId, request.milestoneId());
        validateAssignee(projectId, request.assigneeUserId());
        return taskRepo.save(ProjectTask.builder().projectId(projectId).milestoneId(request.milestoneId())
                .title(request.title()).description(request.description()).assigneeUserId(request.assigneeUserId())
                .createdBy(request.actorUserId()).plannedStartDate(request.plannedStartDate()).dueDate(request.dueDate())
                .priority(blankDefault(request.priority(), "MEDIUM")).status("TODO").progress(0).build());
    }

    @Transactional
    public ProjectTask updateTask(Long taskId, UpdateTaskRequest request) {
        ProjectTask task = task(taskId);
        requireManager(task.getProjectId(), request.actorUserId());
        validateMilestone(task.getProjectId(), request.milestoneId());
        validateAssignee(task.getProjectId(), request.assigneeUserId());
        LocalDate start = request.plannedStartDate() == null ? task.getPlannedStartDate() : request.plannedStartDate();
        LocalDate end = request.dueDate() == null ? task.getDueDate() : request.dueDate();
        validateDates(start, end);
        if (request.milestoneId() != null) task.setMilestoneId(request.milestoneId());
        if (request.title() != null) task.setTitle(request.title());
        if (request.description() != null) task.setDescription(request.description());
        if (request.assigneeUserId() != null) task.setAssigneeUserId(request.assigneeUserId());
        if (request.plannedStartDate() != null) task.setPlannedStartDate(request.plannedStartDate());
        if (request.dueDate() != null) task.setDueDate(request.dueDate());
        if (request.priority() != null) task.setPriority(request.priority());
        return taskRepo.save(task);
    }

    @Transactional
    public ProjectTask updateTaskProgress(Long taskId, TaskProgressRequest request) {
        ProjectTask task = task(taskId);
        if (!isManager(task.getProjectId(), request.actorUserId()) &&
                !request.actorUserId().equals(task.getAssigneeUserId())) {
            throw new SecurityException("只有项目管理者或任务负责人可更新进度");
        }
        int previousProgress = task.getProgress();
        String previousStatus = task.getStatus();
        String status = request.status() == null
                ? (request.progress() == 100 ? "COMPLETED" : request.progress() > 0 ? "IN_PROGRESS" : "TODO")
                : request.status();
        if ("COMPLETED".equals(status)) task.setProgress(100); else task.setProgress(request.progress());
        task.setStatus(status);
        task.setCompletedDate("COMPLETED".equals(status) ? LocalDate.now() : null);
        ProjectTask saved = taskRepo.save(task);
        progressLogRepo.save(TaskProgressLog.builder().taskId(taskId).projectId(task.getProjectId())
                .operatorUserId(request.actorUserId()).previousProgress(previousProgress)
                .currentProgress(saved.getProgress()).previousStatus(previousStatus)
                .currentStatus(saved.getStatus()).comment(request.comment()).build());
        refreshMilestoneProgress(task.getMilestoneId());
        refreshProjectProgress(task.getProjectId());
        return saved;
    }

    @Transactional
    public void deleteTask(Long taskId, Long actorUserId) {
        ProjectTask task = task(taskId);
        requireManager(task.getProjectId(), actorUserId);
        Long milestoneId = task.getMilestoneId();
        taskRepo.delete(task);
        refreshMilestoneProgress(milestoneId);
        refreshProjectProgress(task.getProjectId());
    }

    public List<ProjectTask> listTasks(Long projectId, Long actorUserId) {
        requireParticipant(projectId, actorUserId);
        return taskRepo.findByProjectIdOrderByDueDateAscIdAsc(projectId);
    }

    public List<ProjectTask> listAssignedTasks(Long userId) {
        return taskRepo.findByAssigneeUserIdOrderByDueDateAscIdAsc(userId);
    }

    public List<TaskProgressLog> taskProgressHistory(Long taskId, Long actorUserId) {
        ProjectTask task = task(taskId);
        requireParticipant(task.getProjectId(), actorUserId);
        return progressLogRepo.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    @Transactional
    public AdvisorRecord createAdvisorRecord(Long projectId, AdvisorRecordRequest request) {
        requireParticipant(projectId, request.recorderUserId());
        return advisorRepo.save(AdvisorRecord.builder().projectId(projectId)
                .recorderUserId(request.recorderUserId()).advisorUserId(request.advisorUserId())
                .advisorName(request.advisorName()).guidanceDate(request.guidanceDate()).subject(request.subject())
                .content(request.content()).nextAction(request.nextAction())
                .attachments(join(request.attachments())).build());
    }

    @Transactional
    public AdvisorRecord updateAdvisorRecord(Long recordId, AdvisorRecordRequest request) {
        AdvisorRecord record = advisorRepo.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("指导记录不存在"));
        if (!record.getRecorderUserId().equals(request.recorderUserId()) &&
                !isManager(record.getProjectId(), request.recorderUserId())) {
            throw new SecurityException("无权修改该指导记录");
        }
        record.setAdvisorUserId(request.advisorUserId());
        record.setAdvisorName(request.advisorName());
        record.setGuidanceDate(request.guidanceDate());
        record.setSubject(request.subject());
        record.setContent(request.content());
        record.setNextAction(request.nextAction());
        record.setAttachments(join(request.attachments()));
        return advisorRepo.save(record);
    }

    @Transactional
    public void deleteAdvisorRecord(Long recordId, Long actorUserId) {
        AdvisorRecord record = advisorRepo.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("指导记录不存在"));
        if (!record.getRecorderUserId().equals(actorUserId) && !isManager(record.getProjectId(), actorUserId)) {
            throw new SecurityException("无权删除该指导记录");
        }
        advisorRepo.delete(record);
    }

    public List<AdvisorRecord> listAdvisorRecords(Long projectId, Long actorUserId) {
        requireParticipant(projectId, actorUserId);
        return advisorRepo.findByProjectIdOrderByGuidanceDateDescIdDesc(projectId);
    }

    @Transactional
    public ProjectDeliverable createDeliverable(Long projectId, DeliverableRequest request) {
        requireParticipant(projectId, request.creatorUserId());
        return deliverableRepo.save(ProjectDeliverable.builder().projectId(projectId)
                .creatorUserId(request.creatorUserId()).type(request.type()).title(request.title())
                .description(request.description()).technicalBackground(request.technicalBackground())
                .technicalSolution(request.technicalSolution()).innovations(request.innovations())
                .completionDate(request.completionDate()).fileName(request.fileName()).contentType(request.contentType())
                .fileSize(request.fileSize()).storageKey(request.storageKey()).fileUrl(request.fileUrl())
                .status("DRAFT").build());
    }

    @Transactional
    public ProjectDeliverable updateDeliverable(Long deliverableId, DeliverableRequest request) {
        ProjectDeliverable d = deliverable(deliverableId);
        if (!d.getCreatorUserId().equals(request.creatorUserId()) &&
                !isManager(d.getProjectId(), request.creatorUserId())) {
            throw new SecurityException("无权修改该成果");
        }
        if (d.getLinkedPatentId() != null) throw new IllegalArgumentException("已关联专利的成果不能修改");
        d.setType(request.type()); d.setTitle(request.title()); d.setDescription(request.description());
        d.setTechnicalBackground(request.technicalBackground()); d.setTechnicalSolution(request.technicalSolution());
        d.setInnovations(request.innovations()); d.setCompletionDate(request.completionDate());
        d.setFileName(request.fileName()); d.setContentType(request.contentType()); d.setFileSize(request.fileSize());
        d.setStorageKey(request.storageKey()); d.setFileUrl(request.fileUrl());
        return deliverableRepo.save(d);
    }

    @Transactional
    public ProjectDeliverable linkPatent(Long deliverableId, LinkPatentRequest request) {
        ProjectDeliverable deliverable = deliverable(deliverableId);
        requireManager(deliverable.getProjectId(), request.actorUserId());
        if (!"PATENT_DISCLOSURE".equals(deliverable.getType())) {
            throw new IllegalArgumentException("只有专利交底成果可以关联专利");
        }
        deliverable.setLinkedPatentId(request.patentId());
        deliverable.setStatus("ARCHIVED");
        return deliverableRepo.save(deliverable);
    }

    @Transactional
    public void deleteDeliverable(Long deliverableId, Long actorUserId) {
        ProjectDeliverable d = deliverable(deliverableId);
        if (!d.getCreatorUserId().equals(actorUserId) && !isManager(d.getProjectId(), actorUserId)) {
            throw new SecurityException("无权删除该成果");
        }
        if (d.getLinkedPatentId() != null) throw new IllegalArgumentException("已关联专利的成果不能删除");
        deliverableRepo.delete(d);
    }

    public List<ProjectDeliverable> listDeliverables(Long projectId, Long actorUserId, String type) {
        requireParticipant(projectId, actorUserId);
        return type == null || type.isBlank()
                ? deliverableRepo.findByProjectIdOrderByCreatedAtDesc(projectId)
                : deliverableRepo.findByProjectIdAndTypeOrderByCreatedAtDesc(projectId, type);
    }

    private ResearchProject project(Long id) {
        return projectRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("科研项目不存在"));
    }
    private ProjectMilestone milestone(Long id) {
        return milestoneRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("里程碑不存在"));
    }
    private ProjectTask task(Long id) {
        return taskRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("任务不存在"));
    }
    private ProjectDeliverable deliverable(Long id) {
        return deliverableRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("项目成果不存在"));
    }
    private boolean isActiveMember(Long projectId, Long userId) {
        return memberRepo.existsByProjectIdAndUserIdAndStatus(projectId, userId, "ACTIVE");
    }
    private boolean isManager(Long projectId, Long userId) {
        ResearchProject p = project(projectId);
        if (p.getUserId().equals(userId)) return true;
        return memberRepo.findByProjectIdAndUserId(projectId, userId)
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .map(m -> "OWNER".equals(m.getRole()) || "MANAGER".equals(m.getRole())).orElse(false);
    }
    private void requireOwner(ResearchProject p, Long userId) {
        if (!p.getUserId().equals(userId)) throw new SecurityException("只有项目创建人可以执行此操作");
    }
    private void requireManager(Long projectId, Long userId) {
        if (!isManager(projectId, userId)) throw new SecurityException("只有项目负责人或管理员可以执行此操作");
    }
    private void requireParticipant(Long projectId, Long userId) {
        ResearchProject p = project(projectId);
        if (!p.getUserId().equals(userId) && !isActiveMember(projectId, userId)) {
            throw new SecurityException("用户不是该项目的有效成员");
        }
    }
    private void validateAssignee(Long projectId, Long userId) {
        if (userId != null) requireParticipant(projectId, userId);
    }
    private void validateMilestone(Long projectId, Long milestoneId) {
        if (milestoneId != null && !projectId.equals(milestone(milestoneId).getProjectId())) {
            throw new IllegalArgumentException("里程碑不属于当前项目");
        }
    }
    private void validateDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
    }
    private void requireState(String actual, String... allowed) {
        for (String state : allowed) if (state.equals(actual)) return;
        throw new IllegalStateException("当前状态不允许执行此操作: " + actual);
    }
    private void refreshMilestoneProgress(Long milestoneId) {
        if (milestoneId == null) return;
        ProjectMilestone m = milestone(milestoneId);
        List<ProjectTask> tasks = taskRepo.findByMilestoneIdOrderByDueDateAscIdAsc(milestoneId);
        if (!tasks.isEmpty()) {
            int progress = (int) Math.round(tasks.stream().mapToInt(ProjectTask::getProgress).average().orElse(0));
            m.setProgress(progress);
            m.setStatus(progress == 100 ? "COMPLETED" : progress > 0 ? "IN_PROGRESS" : "PENDING");
            m.setCompletedDate(progress == 100 ? LocalDate.now() : null);
            milestoneRepo.save(m);
        }
    }
    private void refreshProjectProgress(Long projectId) {
        ResearchProject p = project(projectId);
        List<ProjectTask> tasks = taskRepo.findByProjectIdOrderByDueDateAscIdAsc(projectId);
        List<ProjectMilestone> milestones = milestoneRepo.findByProjectIdOrderBySortOrderAscIdAsc(projectId);
        int progress;
        if (!tasks.isEmpty()) progress = (int) Math.round(tasks.stream().mapToInt(ProjectTask::getProgress).average().orElse(0));
        else if (!milestones.isEmpty()) progress = (int) Math.round(milestones.stream().mapToInt(ProjectMilestone::getProgress).average().orElse(0));
        else progress = 0;
        p.setProgress(progress);
        if (progress == 100) p.setStatus("COMPLETED");
        projectRepo.save(p);
    }
    private String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
    private String join(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join(",", values);
    }
}
