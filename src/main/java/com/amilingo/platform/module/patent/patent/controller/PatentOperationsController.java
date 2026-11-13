package com.amilingo.platform.module.patent.controller;
import com.amilingo.platform.common.dto.Result;import com.amilingo.platform.module.patent.dto.PatentOperationsDTO.*;import com.amilingo.platform.module.patent.entity.*;import com.amilingo.platform.module.patent.service.PatentOperationsService;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequestMapping("/api/patent") @RequiredArgsConstructor
public class PatentOperationsController{
 private final PatentOperationsService service;
 @GetMapping("/{patentId}/documents")public Result<List<PatentDocument>>documents(@PathVariable Long patentId,@RequestParam Long actorUserId,@RequestParam(required=false)String category){return Result.ok(service.documents(patentId,actorUserId,category));}
 @PostMapping("/{patentId}/documents")public Result<PatentDocument>addDocument(@PathVariable Long patentId,@Valid@RequestBody DocumentRequest r){return Result.ok(service.addDocument(patentId,r));}
 @DeleteMapping("/documents/{documentId}")public Result<Void>deleteDocument(@PathVariable Long documentId,@RequestParam Long actorUserId){service.deleteDocument(documentId,actorUserId);return Result.ok();}
 @GetMapping("/{patentId}/claims")public Result<List<PatentClaim>>claims(@PathVariable Long patentId,@RequestParam Long actorUserId){return Result.ok(service.claims(patentId,actorUserId));}
 @GetMapping("/{patentId}/claims/{claimNo}/versions")public Result<List<PatentClaim>>claimVersions(@PathVariable Long patentId,@PathVariable Integer claimNo,@RequestParam Long actorUserId){return Result.ok(service.claimVersions(patentId,claimNo,actorUserId));}
 @PostMapping("/{patentId}/claims")public Result<PatentClaim>createClaim(@PathVariable Long patentId,@Valid@RequestBody ClaimRequest r){return Result.ok(service.createClaim(patentId,r));}
 @PostMapping("/claims/{claimId}/revisions")public Result<PatentClaim>reviseClaim(@PathVariable Long claimId,@Valid@RequestBody ClaimRequest r){return Result.ok(service.reviseClaim(claimId,r));}
 @GetMapping("/{patentId}/office-actions")public Result<List<PatentOfficeAction>>actions(@PathVariable Long patentId,@RequestParam Long actorUserId){return Result.ok(service.officeActions(patentId,actorUserId));}
 @PostMapping("/{patentId}/office-actions")public Result<PatentOfficeAction>createAction(@PathVariable Long patentId,@Valid@RequestBody OfficeActionRequest r){return Result.ok(service.createOfficeAction(patentId,r));}
 @PostMapping("/office-actions/{actionId}/response")public Result<PatentOfficeAction>respond(@PathVariable Long actionId,@Valid@RequestBody OfficeActionResponseRequest r){return Result.ok(service.respondOfficeAction(actionId,r));}
 @GetMapping("/{patentId}/deadlines")public Result<List<DeadlineView>>deadlines(@PathVariable Long patentId,@RequestParam Long actorUserId){return Result.ok(service.deadlines(patentId,actorUserId));}
 @GetMapping("/deadlines/reminders")public Result<List<DeadlineView>>reminders(@RequestParam Long actorUserId,@RequestParam(defaultValue="7")int days){return Result.ok(service.dueReminders(actorUserId,days));}
 @PostMapping("/{patentId}/deadlines")public Result<PatentDeadline>createDeadline(@PathVariable Long patentId,@Valid@RequestBody DeadlineRequest r){return Result.ok(service.createDeadline(patentId,r));}
 @PostMapping("/deadlines/{deadlineId}/status")public Result<PatentDeadline>deadlineStatus(@PathVariable Long deadlineId,@Valid@RequestBody DeadlineStatusRequest r){return Result.ok(service.updateDeadlineStatus(deadlineId,r));}
 @GetMapping("/{patentId}/fees")public Result<List<PatentFee>>fees(@PathVariable Long patentId,@RequestParam Long actorUserId){return Result.ok(service.fees(patentId,actorUserId));}
 @GetMapping("/{patentId}/fees/summary")public Result<FeeSummary>feeSummary(@PathVariable Long patentId,@RequestParam Long actorUserId){return Result.ok(service.feeSummary(patentId,actorUserId));}
 @PostMapping("/{patentId}/fees")public Result<PatentFee>createFee(@PathVariable Long patentId,@Valid@RequestBody FeeRequest r){return Result.ok(service.createFee(patentId,r));}
 @PostMapping("/fees/{feeId}/payment")public Result<PatentFee>payFee(@PathVariable Long feeId,@Valid@RequestBody FeePaymentRequest r){return Result.ok(service.payFee(feeId,r));}
 @DeleteMapping("/fees/{feeId}")public Result<Void>deleteFee(@PathVariable Long feeId,@RequestParam Long actorUserId){service.deleteFee(feeId,actorUserId);return Result.ok();}
}
