package com.amilingo.platform.module.patent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 专利技术交底书及审核状态。 */
@Entity @Table(name="patent_disclosure")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatentDisclosure {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true) private Long patentId;
    @Column(columnDefinition="TEXT") private String backgroundTechnology;
    @Column(columnDefinition="TEXT") private String existingProblems;
    @Column(columnDefinition="TEXT") private String technicalProblem;
    @Column(columnDefinition="LONGTEXT") private String technicalSolution;
    @Column(columnDefinition="TEXT") private String innovations;
    @Column(columnDefinition="TEXT") private String beneficialEffects;
    @Column(columnDefinition="LONGTEXT") private String embodiments;
    @Column(columnDefinition="TEXT") private String drawingDescription;
    @Column(columnDefinition="TEXT") private String keyParameters;
    @Column(columnDefinition="TEXT") private String alternativeSolutions;
    private Integer technologyReadinessLevel;
    @Column(columnDefinition="TEXT") private String attachments;
    @Column(nullable=false) private String status;
    private Long submittedBy;
    private Long reviewedBy;
    @Column(columnDefinition="TEXT") private String reviewOpinion;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist void onCreate(){if(status==null)status="DRAFT";createdAt=updatedAt=LocalDateTime.now();}
    @PreUpdate void onUpdate(){updatedAt=LocalDateTime.now();}
}
