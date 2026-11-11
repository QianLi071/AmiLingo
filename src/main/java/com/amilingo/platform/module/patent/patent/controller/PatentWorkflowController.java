package com.amilingo.platform.module.patent.controller;
import com.amilingo.platform.common.dto.Result;import com.amilingo.platform.module.patent.dto.PatentWorkflowDTO.*;import com.amilingo.platform.module.patent.entity.*;import com.amilingo.platform.module.patent.service.PatentWorkflowService;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequestMapping("/api/patent") @RequiredArgsConstructor
public class PatentWorkflowController{
 private final PatentWorkflowService service;
 @GetMapping("/{patentId}/disclosure")public Result<PatentDisclosure> disclosure(@PathVariable Long patentId,@RequestParam Long actorUserId){return Result.ok(service.getDisclosure(patentId,actorUserId));}
 @PutMapping("/{patentId}/disclosure")public Result<PatentDisclosure> saveDisclosure(@PathVariable Long patentId,@Valid @RequestBody DisclosureRequest r){return Result.ok(service.saveDisclosure(patentId,r));}
 @PostMapping("/{patentId}/disclosure/action")public Result<PatentDisclosure> disclosureAction(@PathVariable Long patentId,@Valid @RequestBody DisclosureActionRequest r){return Result.ok(service.actDisclosure(patentId,r));}
 @PostMapping("/{patentId}/status")public Result<Patent> status(@PathVariable Long patentId,@Valid @RequestBody StatusChangeRequest r){return Result.ok(service.changeStatus(patentId,r));}
 @GetMapping("/{patentId}/status-history")public Result<List<PatentStatusHistory>> history(@PathVariable Long patentId,@RequestParam Long actorUserId){return Result.ok(service.statusHistory(patentId,actorUserId));}
 @GetMapping("/{patentId}/project-links")public Result<List<PatentProjectLink>> links(@PathVariable Long patentId,@RequestParam Long actorUserId){return Result.ok(service.projectLinks(patentId,actorUserId));}
 @PostMapping("/{patentId}/project-links")public Result<PatentProjectLink> link(@PathVariable Long patentId,@Valid @RequestBody ProjectLinkRequest r){return Result.ok(service.linkProject(patentId,r));}
 @DeleteMapping("/project-links/{linkId}")public Result<Void> unlink(@PathVariable Long linkId,@RequestParam Long actorUserId){service.unlinkProject(linkId,actorUserId);return Result.ok();}
 @GetMapping("/{patentId}/material-cards")public Result<List<PatentMaterialCard>> cards(@PathVariable Long patentId,@RequestParam Long actorUserId){return Result.ok(service.cards(patentId,actorUserId));}
 @GetMapping("/{patentId}/material-cards/{cardNo}/versions")public Result<List<PatentMaterialCard>> cardVersions(@PathVariable Long patentId,@PathVariable Integer cardNo,@RequestParam Long actorUserId){return Result.ok(service.cardVersions(patentId,cardNo,actorUserId));}
 @PostMapping("/{patentId}/material-cards")public Result<PatentMaterialCard> addCard(@PathVariable Long patentId,@Valid @RequestBody MaterialCardRequest r){return Result.ok(service.createCard(patentId,r));}
 @PostMapping("/{patentId}/material-cards/generate-from-disclosure")public Result<List<PatentMaterialCard>> generateCards(@PathVariable Long patentId,@Valid @RequestBody MaterialCardGenerationRequest r){return Result.ok(service.generateCardsFromDisclosure(patentId,r));}
 @PostMapping("/material-cards/{cardId}/revisions")public Result<PatentMaterialCard> reviseCard(@PathVariable Long cardId,@Valid @RequestBody MaterialCardRequest r){return Result.ok(service.reviseCard(cardId,r));}
 @GetMapping("/{patentId}/inventors")public Result<List<PatentInventor>> inventors(@PathVariable Long patentId,@RequestParam Long actorUserId){return Result.ok(service.inventors(patentId,actorUserId));}
 @PostMapping("/{patentId}/inventors")public Result<PatentInventor> addInventor(@PathVariable Long patentId,@Valid @RequestBody InventorRequest r){return Result.ok(service.addInventor(patentId,r));}
 @PutMapping("/inventors/{inventorId}")public Result<PatentInventor> updateInventor(@PathVariable Long inventorId,@Valid @RequestBody InventorRequest r){return Result.ok(service.updateInventor(inventorId,r));}
 @PostMapping("/inventors/{inventorId}/confirmation")public Result<PatentInventor> confirmInventor(@PathVariable Long inventorId,@Valid @RequestBody InventorConfirmationRequest r){return Result.ok(service.confirmInventor(inventorId,r));}
 @DeleteMapping("/inventors/{inventorId}")public Result<Void> deleteInventor(@PathVariable Long inventorId,@RequestParam Long actorUserId){service.deleteInventor(inventorId,actorUserId);return Result.ok();}
}
