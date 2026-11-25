package com.amilingo.platform.module.paper.service;

import com.amilingo.platform.module.paper.dto.PaperWorkflowDTO.*;
import com.amilingo.platform.module.paper.entity.*;
import com.amilingo.platform.module.paper.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/** 论文从选题、写作、投稿到审稿的业务工作流。 */
@Service @RequiredArgsConstructor
public class PaperWorkflowService {
    private final PaperRepository paperRepo;
    private final PaperOutlineSectionRepository outlineRepo;
    private final PaperDraftVersionRepository versionRepo;
    private final PaperSubmissionRepository submissionRepo;
    private final PaperFeedbackRepository feedbackRepo;
    private final PaperReviewRecordRepository reviewRepo;
    private final PaperStatusHistoryRepository historyRepo;
    private final PaperAccessService access;

    @Transactional
    public Paper updateTopic(Long paperId, TopicRequest r) {
        Paper p = ownedPaper(paperId, r.actorUserId());
        p.setTitle(r.title()); p.setTopicBackground(r.topicBackground());
        p.setResearchDirection(r.researchDirection()); p.setResearchObjective(r.researchObjective());
        p.setResearchQuestion(r.researchQuestion()); p.setKeywords(r.keywords());
        p.setTopicSource(r.topicSource()); p.setSupervisor(r.supervisor());
        p.setExpectedCompletionDate(r.expectedCompletionDate());
        return paperRepo.save(p);
    }

    @Transactional
    public Paper changeTopicStatus(Long paperId, TopicStatusRequest r) {
        Paper p = ownedPaper(paperId, r.actorUserId());
        if ("ARCHIVED".equals(p.getTopicStatus()) && !"ARCHIVED".equals(r.status()))
            throw new IllegalStateException("已归档选题不能恢复");
        p.setTopicStatus(r.status());
        return paperRepo.save(p);
    }

    @Transactional
    public PaperOutlineSection createOutline(Long paperId, OutlineSectionRequest r) {
        ownedPaper(paperId, r.actorUserId());
        validateParent(paperId, r.parentId());
        return outlineRepo.save(PaperOutlineSection.builder().paperId(paperId).parentId(r.parentId())
                .title(r.title()).summary(r.summary()).sortOrder(r.sortOrder()).build());
    }

    @Transactional
    public PaperOutlineSection updateOutline(Long sectionId, OutlineSectionRequest r) {
        PaperOutlineSection s = outline(sectionId);
        ownedPaper(s.getPaperId(), r.actorUserId());
        validateParent(s.getPaperId(), r.parentId());
        if (sectionId.equals(r.parentId())) throw new IllegalArgumentException("章节不能以自身为父章节");
        s.setParentId(r.parentId()); s.setTitle(r.title()); s.setSummary(r.summary());
        if (r.sortOrder() != null) s.setSortOrder(r.sortOrder());
        return outlineRepo.save(s);
    }

    @Transactional
    public void deleteOutline(Long sectionId, Long actorUserId) {
        PaperOutlineSection s = outline(sectionId);
        ownedPaper(s.getPaperId(), actorUserId);
        boolean hasChildren = outlineRepo.findByPaperIdOrderBySortOrderAscIdAsc(s.getPaperId()).stream()
                .anyMatch(child -> sectionId.equals(child.getParentId()));
        if (hasChildren) throw new IllegalArgumentException("请先删除子章节");
        outlineRepo.delete(s);
    }

    public List<PaperOutlineSection> listOutline(Long paperId, Long actorUserId) {
        access.requireRead(paperId, actorUserId);
        return outlineRepo.findByPaperIdOrderBySortOrderAscIdAsc(paperId);
    }

    @Transactional
    public PaperDraftVersion createVersion(Long paperId, DraftVersionRequest r) {
        Paper p = ownedPaper(paperId, r.actorUserId());
        versionRepo.findByPaperIdAndCurrentVersionTrue(paperId).ifPresent(old -> {
            old.setCurrentVersion(false); versionRepo.save(old);
        });
        int next = versionRepo.findFirstByPaperIdOrderByVersionNoDesc(paperId)
                .map(v -> v.getVersionNo() + 1).orElse(1);
        if ("DRAFT".equals(p.getTopicStatus())) p.setTopicStatus("CONFIRMED");
        p.setStatus("WRITING"); paperRepo.save(p);
        return versionRepo.save(PaperDraftVersion.builder().paperId(paperId).versionNo(next)
                .versionType(r.versionType()).content(r.content()).wordCount(wordCount(r.content()))
                .changeNote(r.changeNote()).createdBy(r.actorUserId()).currentVersion(true).build());
    }

    @Transactional
    public PaperDraftVersion restoreVersion(Long versionId, RestoreVersionRequest r) {
        PaperDraftVersion source = version(versionId);
        ownedPaper(source.getPaperId(), r.actorUserId());
        return createVersion(source.getPaperId(), new DraftVersionRequest(r.actorUserId(),
                "REVISION", source.getContent(), r.changeNote() == null ? "恢复自版本 v" + source.getVersionNo() : r.changeNote()));
    }

    public List<PaperDraftVersion> listVersions(Long paperId, Long actorUserId) {
        access.requireRead(paperId, actorUserId);
        return versionRepo.findByPaperIdOrderByVersionNoDesc(paperId);
    }

    @Transactional
    public PaperSubmission createSubmission(Long paperId, SubmissionRequest r) {
        Paper p = ownedPaper(paperId, r.actorUserId());
        validateVersion(paperId, r.draftVersionId());
        int round = submissionRepo.findFirstByPaperIdOrderByRoundNoDesc(paperId)
                .map(s -> s.getRoundNo() + 1).orElse(1);
        PaperSubmission result = submissionRepo.save(PaperSubmission.builder().paperId(paperId).roundNo(round)
                .venueName(r.venueName()).venueType(r.venueType()).submissionPlatform(r.submissionPlatform())
                .manuscriptNumber(r.manuscriptNumber()).correspondingAuthor(r.correspondingAuthor())
                .authorSnapshot(r.authorSnapshot()).draftVersionId(r.draftVersionId())
                .submissionDate(r.submissionDate()).status("SUBMITTED").notes(r.notes())
                .attachments(join(r.attachments())).build());
        p.setJournal(r.venueName()); p.setSubmissionDate(r.submissionDate());
        changePaperStatus(p, "SUBMITTED", r.actorUserId(), "第 " + round + " 次投稿");
        return result;
    }

    @Transactional
    public PaperSubmission changeSubmissionStatus(Long submissionId, SubmissionStatusRequest r) {
        PaperSubmission s = submission(submissionId);
        Paper p = ownedPaper(s.getPaperId(), r.actorUserId());
        s.setStatus(r.status());
        if (r.notes() != null) s.setNotes(r.notes());
        String paperStatus = switch (r.status()) {
            case "ACCEPTED" -> "ACCEPTED";
            case "REJECTED", "WITHDRAWN" -> "WRITING";
            default -> "SUBMITTED";
        };
        changePaperStatus(p, paperStatus, r.actorUserId(), "投稿状态变更为 " + r.status());
        return submissionRepo.save(s);
    }

    public List<PaperSubmission> listSubmissions(Long paperId, Long actorUserId) {
        access.requireRead(paperId, actorUserId);
        return submissionRepo.findByPaperIdOrderByRoundNoDesc(paperId);
    }

    @Transactional
    public PaperFeedback createFeedback(Long paperId, FeedbackRequest r) {
        ownedPaper(paperId, r.actorUserId()); validateVersion(paperId, r.draftVersionId());
        return feedbackRepo.save(PaperFeedback.builder().paperId(paperId).draftVersionId(r.draftVersionId())
                .feedbackUserId(r.feedbackUserId()).feedbackAuthor(r.feedbackAuthor()).feedbackDate(r.feedbackDate())
                .subject(r.subject()).content(r.content()).modificationRequirement(r.modificationRequirement())
                .dueDate(r.dueDate()).status("PENDING").attachments(join(r.attachments())).build());
    }

    @Transactional
    public PaperFeedback updateFeedbackProgress(Long feedbackId, FeedbackProgressRequest r) {
        PaperFeedback f = feedback(feedbackId); ownedPaper(f.getPaperId(), r.actorUserId());
        validateVersion(f.getPaperId(), r.resolvedVersionId());
        if ("RESOLVED".equals(r.status()) && r.resolvedVersionId() == null)
            throw new IllegalArgumentException("解决反馈时必须关联修改后的论文版本");
        f.setStatus(r.status()); f.setStudentReply(r.studentReply()); f.setResolvedVersionId(r.resolvedVersionId());
        f.setResolvedAt("RESOLVED".equals(r.status()) ? LocalDateTime.now() : null);
        return feedbackRepo.save(f);
    }

    public List<PaperFeedback> listFeedback(Long paperId, Long actorUserId) {
        access.requireRead(paperId, actorUserId);
        return feedbackRepo.findByPaperIdOrderByFeedbackDateDescIdDesc(paperId);
    }

    @Transactional
    public PaperReviewRecord createReviewRecord(Long paperId, ReviewRecordRequest r) {
        Paper p = ownedPaper(paperId, r.actorUserId());
        validateSubmission(paperId, r.submissionId()); validateVersion(paperId, r.responseDraftVersionId());
        if (r.revisionDeadline() != null && r.revisionDeadline().isBefore(r.statusDate()))
            throw new IllegalArgumentException("返修截止日期不能早于状态日期");
        PaperReviewRecord record = reviewRepo.save(PaperReviewRecord.builder().paperId(paperId)
                .submissionId(r.submissionId()).reviewRound(r.reviewRound()).status(r.status())
                .statusDate(r.statusDate()).reviewComment(r.reviewComment()).revisionDeadline(r.revisionDeadline())
                .responseDraftVersionId(r.responseDraftVersionId()).attachments(join(r.attachments()))
                .operatorUserId(r.actorUserId()).build());
        String paperStatus = switch (r.status()) {
            case "ACCEPTED" -> "ACCEPTED";
            case "PUBLISHED" -> "PUBLISHED";
            case "REJECTED" -> "WRITING";
            default -> "SUBMITTED";
        };
        if ("ACCEPTED".equals(r.status())) p.setAcceptanceDate(r.statusDate());
        if ("PUBLISHED".equals(r.status())) p.setPublicationDate(r.statusDate());
        changePaperStatus(p, paperStatus, r.actorUserId(), "审稿状态变更为 " + r.status());
        return record;
    }

    public List<PaperReviewRecord> listReviews(Long paperId, Long actorUserId) {
        access.requireRead(paperId, actorUserId);
        return reviewRepo.findByPaperIdOrderByStatusDateDescIdDesc(paperId);
    }
    public List<PaperStatusHistory> statusHistory(Long paperId, Long actorUserId) {
        access.requireRead(paperId, actorUserId);
        return historyRepo.findByPaperIdOrderByCreatedAtDesc(paperId);
    }

    private void changePaperStatus(Paper p, String status, Long userId, String remark) {
        String previous = p.getStatus(); p.setStatus(status); paperRepo.save(p);
        if (!status.equals(previous)) historyRepo.save(PaperStatusHistory.builder().paperId(p.getId())
                .previousStatus(previous).currentStatus(status).operatorUserId(userId).remark(remark).build());
    }
    private Paper ownedPaper(Long id, Long userId) {
        return access.requireEdit(id, userId);
    }
    private PaperOutlineSection outline(Long id) { return outlineRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("大纲章节不存在")); }
    private PaperDraftVersion version(Long id) { return versionRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("论文版本不存在")); }
    private PaperSubmission submission(Long id) { return submissionRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("投稿记录不存在")); }
    private PaperFeedback feedback(Long id) { return feedbackRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("反馈记录不存在")); }
    private void validateParent(Long paperId, Long parentId) {
        if (parentId != null && !paperId.equals(outline(parentId).getPaperId())) throw new IllegalArgumentException("父章节不属于当前论文");
    }
    private void validateVersion(Long paperId, Long versionId) {
        if (versionId != null && !paperId.equals(version(versionId).getPaperId())) throw new IllegalArgumentException("论文版本不属于当前论文");
    }
    private void validateSubmission(Long paperId, Long id) {
        if (id != null && !paperId.equals(submission(id).getPaperId())) throw new IllegalArgumentException("投稿记录不属于当前论文");
    }
    private int wordCount(String value) {
        if (value == null || value.isBlank()) return 0;
        String compact = value.trim();
        long cjk = compact.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        String latin = compact.replaceAll("[\\u4E00-\\u9FFF]", " ").trim();
        return Math.toIntExact(cjk + (latin.isBlank() ? 0 : latin.split("\\s+").length));
    }
    private String join(List<String> list) { return list == null || list.isEmpty() ? null : String.join(",", list); }
}
