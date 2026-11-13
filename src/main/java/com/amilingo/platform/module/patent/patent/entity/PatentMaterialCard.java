package com.amilingo.platform.module.patent.entity;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
/** 可从交底书或科研成果整理生成的专利素材卡。 */
@Entity @Table(name="patent_material_card",uniqueConstraints=@UniqueConstraint(name="uk_patent_card_version",columnNames={"patent_id","card_no","version_no"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatentMaterialCard{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="patent_id",nullable=false) private Long patentId; @Column(name="card_no",nullable=false) private Integer cardNo;
 @Column(name="version_no",nullable=false) private Integer versionNo; @Column(nullable=false) private String category;
 @Column(nullable=false) private String title; @Column(columnDefinition="TEXT") private String technicalProblemSummary;
 @Column(columnDefinition="LONGTEXT") private String technicalSolutionSummary; @Column(columnDefinition="TEXT") private String innovations;
 @Column(columnDefinition="TEXT") private String experimentalData; @Column(columnDefinition="TEXT") private String technicalEffects;
 @Column(columnDefinition="TEXT") private String drawings; private String keywords;
 @Column(columnDefinition="TEXT") private String claimFeatures; private String sourceType; private Long sourceId;
 @Column(nullable=false) private Long createdBy; @Column(nullable=false) private Boolean currentVersion; private LocalDateTime createdAt;
 @PrePersist void onCreate(){if(versionNo==null)versionNo=1;if(currentVersion==null)currentVersion=true;createdAt=LocalDateTime.now();}
}
