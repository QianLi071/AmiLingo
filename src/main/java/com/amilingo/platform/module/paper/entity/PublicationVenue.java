package com.amilingo.platform.module.paper.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 用户维护的期刊或会议档案。 */
@Entity @Table(name="paper_publication_venue")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PublicationVenue {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long ownerUserId;
    @Column(nullable=false) private String name;
    @Column(nullable=false) private String type;
    private String issn;
    private String publisher;
    private String subjectCategory;
    private String sciQuartile;
    private String casQuartile;
    private BigDecimal impactFactor;
    private String ccfLevel;
    private Boolean eiIndexed;
    private Boolean coreJournal;
    private String submissionUrl;
    private Integer typicalReviewDays;
    private BigDecimal publicationFee;
    private String currency;
    @Column(columnDefinition="TEXT") private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist void onCreate(){ if(eiIndexed==null)eiIndexed=false; if(coreJournal==null)coreJournal=false; createdAt=updatedAt=LocalDateTime.now(); }
    @PreUpdate void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
