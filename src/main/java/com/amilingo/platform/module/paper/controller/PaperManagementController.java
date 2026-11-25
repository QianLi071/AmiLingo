package com.amilingo.platform.module.paper.controller;

import com.amilingo.platform.common.dto.Result;
import com.amilingo.platform.module.paper.dto.PaperManagementDTO.*;
import com.amilingo.platform.module.paper.entity.*;
import com.amilingo.platform.module.paper.service.PaperManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/paper") @RequiredArgsConstructor
public class PaperManagementController {
    private final PaperManagementService service;

    @GetMapping("/{paperId}/authors") public Result<List<PaperAuthor>> authors(@PathVariable Long paperId,@RequestParam Long actorUserId){return Result.ok(service.authors(paperId,actorUserId));}
    @PostMapping("/{paperId}/authors") public Result<PaperAuthor> addAuthor(@PathVariable Long paperId,@Valid @RequestBody AuthorRequest r){return Result.ok(service.addAuthor(paperId,r));}
    @PutMapping("/authors/{authorId}") public Result<PaperAuthor> updateAuthor(@PathVariable Long authorId,@Valid @RequestBody AuthorRequest r){return Result.ok(service.updateAuthor(authorId,r));}
    @DeleteMapping("/authors/{authorId}") public Result<Void> deleteAuthor(@PathVariable Long authorId,@RequestParam Long actorUserId){service.deleteAuthor(authorId,actorUserId);return Result.ok();}

    @GetMapping("/venues") public Result<List<PublicationVenue>> venues(@RequestParam Long actorUserId,@RequestParam(required=false) String type){return Result.ok(service.venues(actorUserId,type));}
    @PostMapping("/venues") public Result<PublicationVenue> createVenue(@Valid @RequestBody VenueRequest r){return Result.ok(service.createVenue(r));}
    @PutMapping("/venues/{venueId}") public Result<PublicationVenue> updateVenue(@PathVariable Long venueId,@Valid @RequestBody VenueRequest r){return Result.ok(service.updateVenue(venueId,r));}
    @DeleteMapping("/venues/{venueId}") public Result<Void> deleteVenue(@PathVariable Long venueId,@RequestParam Long actorUserId){service.deleteVenue(venueId,actorUserId);return Result.ok();}

    @GetMapping("/{paperId}/attachments") public Result<List<PaperAttachment>> attachments(@PathVariable Long paperId,@RequestParam Long actorUserId,@RequestParam(required=false) String category){return Result.ok(service.attachments(paperId,actorUserId,category));}
    @PostMapping("/{paperId}/attachments") public Result<PaperAttachment> addAttachment(@PathVariable Long paperId,@Valid @RequestBody AttachmentRequest r){return Result.ok(service.addAttachment(paperId,r));}
    @DeleteMapping("/attachments/{attachmentId}") public Result<Void> deleteAttachment(@PathVariable Long attachmentId,@RequestParam Long actorUserId){service.deleteAttachment(attachmentId,actorUserId);return Result.ok();}

    @GetMapping("/{paperId}/collaborators") public Result<List<PaperCollaborator>> collaborators(@PathVariable Long paperId,@RequestParam Long actorUserId){return Result.ok(service.collaborators(paperId,actorUserId));}
    @PostMapping("/{paperId}/collaborators") public Result<PaperCollaborator> addCollaborator(@PathVariable Long paperId,@Valid @RequestBody CollaboratorRequest r){return Result.ok(service.addCollaborator(paperId,r));}
    @PutMapping("/collaborators/{collaboratorId}") public Result<PaperCollaborator> updateCollaborator(@PathVariable Long collaboratorId,@Valid @RequestBody CollaboratorUpdateRequest r){return Result.ok(service.updateCollaborator(collaboratorId,r));}
    @DeleteMapping("/collaborators/{collaboratorId}") public Result<Void> removeCollaborator(@PathVariable Long collaboratorId,@RequestParam Long actorUserId){service.removeCollaborator(collaboratorId,actorUserId);return Result.ok();}

    @PostMapping("/search") public Result<Page<Paper>> search(@Valid @RequestBody PaperSearchRequest r){return Result.ok(service.search(r));}
    @GetMapping("/statistics") public Result<PaperStatistics> statistics(@RequestParam Long actorUserId){return Result.ok(service.statistics(actorUserId));}
}
