package com.amilingo.platform.module.paper.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** 作者、刊会、附件、检索统计和权限管理 DTO。 */
public final class PaperManagementDTO {
    private PaperManagementDTO(){}
    public record AuthorRequest(@NotNull Long actorUserId,Long userId,@NotBlank @Size(max=255) String name,
        @Size(max=255) String institution,@NotNull @Min(1) Integer authorOrder,Boolean coFirstAuthor,
        Boolean correspondingAuthor,@Size(max=4000) String contribution){}
    public record VenueRequest(@NotNull Long actorUserId,@NotBlank @Size(max=255) String name,
        @NotBlank @Pattern(regexp="JOURNAL|CONFERENCE|OTHER") String type,String issn,String publisher,
        String subjectCategory,String sciQuartile,String casQuartile,@PositiveOrZero BigDecimal impactFactor,
        String ccfLevel,Boolean eiIndexed,Boolean coreJournal,String submissionUrl,
        @PositiveOrZero Integer typicalReviewDays,@PositiveOrZero BigDecimal publicationFee,String currency,String notes){}
    public record AttachmentRequest(@NotNull Long actorUserId,Long relatedRecordId,
        @NotBlank @Pattern(regexp="OUTLINE|MANUSCRIPT|SUBMISSION|COVER_LETTER|REVIEW_COMMENT|RESPONSE|ACCEPTANCE|PROOF|PUBLICATION|OTHER") String category,
        @NotBlank String fileName,String contentType,@PositiveOrZero Long fileSize,@NotBlank String storageKey,
        String fileUrl,String fileHash,@Min(1) Integer versionNo){}
    public record CollaboratorRequest(@NotNull Long actorUserId,@NotNull Long userId,
        @NotBlank @Pattern(regexp="EDITOR|REVIEWER|VIEWER") String role){}
    public record CollaboratorUpdateRequest(@NotNull Long actorUserId,
        @Pattern(regexp="EDITOR|REVIEWER|VIEWER") String role,@Pattern(regexp="ACTIVE|INACTIVE") String status){}
    public record PaperSearchRequest(@NotNull Long actorUserId,String keyword,String status,String level,
        String journal,Integer year,Integer authorOrder,LocalDate fromDate,LocalDate toDate,
        @Min(0) Integer page,@Min(1) @Max(100) Integer size,String sortBy,String sortDirection){}
    public record PaperStatistics(long total,Map<String,Long> byStatus,Map<String,Long> byLevel,
        Map<Integer,Long> byYear,double acceptanceRate,double averageReviewDays){}
}
