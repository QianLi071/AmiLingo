package com.amilingo.platform.module.paper.controller;

import com.amilingo.platform.common.dto.Result;
import com.amilingo.platform.module.paper.dto.PaperWorkflowDTO.*;
import com.amilingo.platform.module.paper.entity.*;
import com.amilingo.platform.module.paper.service.PaperWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/paper") @RequiredArgsConstructor
public class PaperWorkflowController {
    private final PaperWorkflowService service;

    @PutMapping("/{paperId}/topic") public Result<Paper> topic(@PathVariable Long paperId, @Valid @RequestBody TopicRequest r) { return Result.ok(service.updateTopic(paperId, r)); }
    @PostMapping("/{paperId}/topic/status") public Result<Paper> topicStatus(@PathVariable Long paperId, @Valid @RequestBody TopicStatusRequest r) { return Result.ok(service.changeTopicStatus(paperId, r)); }

    @GetMapping("/{paperId}/outline") public Result<List<PaperOutlineSection>> outline(@PathVariable Long paperId, @RequestParam Long actorUserId) { return Result.ok(service.listOutline(paperId, actorUserId)); }
    @PostMapping("/{paperId}/outline") public Result<PaperOutlineSection> addOutline(@PathVariable Long paperId, @Valid @RequestBody OutlineSectionRequest r) { return Result.ok(service.createOutline(paperId, r)); }
    @PutMapping("/outline/{sectionId}") public Result<PaperOutlineSection> updateOutline(@PathVariable Long sectionId, @Valid @RequestBody OutlineSectionRequest r) { return Result.ok(service.updateOutline(sectionId, r)); }
    @DeleteMapping("/outline/{sectionId}") public Result<Void> deleteOutline(@PathVariable Long sectionId, @RequestParam Long actorUserId) { service.deleteOutline(sectionId, actorUserId); return Result.ok(); }

    @GetMapping("/{paperId}/versions") public Result<List<PaperDraftVersion>> versions(@PathVariable Long paperId, @RequestParam Long actorUserId) { return Result.ok(service.listVersions(paperId, actorUserId)); }
    @PostMapping("/{paperId}/versions") public Result<PaperDraftVersion> addVersion(@PathVariable Long paperId, @Valid @RequestBody DraftVersionRequest r) { return Result.ok(service.createVersion(paperId, r)); }
    @PostMapping("/versions/{versionId}/restore") public Result<PaperDraftVersion> restore(@PathVariable Long versionId, @Valid @RequestBody RestoreVersionRequest r) { return Result.ok(service.restoreVersion(versionId, r)); }

    @GetMapping("/{paperId}/submissions") public Result<List<PaperSubmission>> submissions(@PathVariable Long paperId, @RequestParam Long actorUserId) { return Result.ok(service.listSubmissions(paperId, actorUserId)); }
    @PostMapping("/{paperId}/submissions") public Result<PaperSubmission> submit(@PathVariable Long paperId, @Valid @RequestBody SubmissionRequest r) { return Result.ok(service.createSubmission(paperId, r)); }
    @PostMapping("/submissions/{submissionId}/status") public Result<PaperSubmission> submissionStatus(@PathVariable Long submissionId, @Valid @RequestBody SubmissionStatusRequest r) { return Result.ok(service.changeSubmissionStatus(submissionId, r)); }

    @GetMapping("/{paperId}/feedback") public Result<List<PaperFeedback>> feedback(@PathVariable Long paperId, @RequestParam Long actorUserId) { return Result.ok(service.listFeedback(paperId, actorUserId)); }
    @PostMapping("/{paperId}/feedback") public Result<PaperFeedback> addFeedback(@PathVariable Long paperId, @Valid @RequestBody FeedbackRequest r) { return Result.ok(service.createFeedback(paperId, r)); }
    @PostMapping("/feedback/{feedbackId}/progress") public Result<PaperFeedback> feedbackProgress(@PathVariable Long feedbackId, @Valid @RequestBody FeedbackProgressRequest r) { return Result.ok(service.updateFeedbackProgress(feedbackId, r)); }

    @GetMapping("/{paperId}/reviews") public Result<List<PaperReviewRecord>> reviews(@PathVariable Long paperId, @RequestParam Long actorUserId) { return Result.ok(service.listReviews(paperId, actorUserId)); }
    @PostMapping("/{paperId}/reviews") public Result<PaperReviewRecord> addReview(@PathVariable Long paperId, @Valid @RequestBody ReviewRecordRequest r) { return Result.ok(service.createReviewRecord(paperId, r)); }
    @GetMapping("/{paperId}/status-history") public Result<List<PaperStatusHistory>> statusHistory(@PathVariable Long paperId, @RequestParam Long actorUserId) { return Result.ok(service.statusHistory(paperId, actorUserId)); }
}
