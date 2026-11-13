package com.amilingo.platform.module.patent.entity;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
/** 独立/从属权利要求的版本快照。 */
@Entity @Table(name="patent_claim",uniqueConstraints=@UniqueConstraint(name="uk_patent_claim_version",columnNames={"patent_id","claim_no","version_no"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatentClaim{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;@Column(name="patent_id",nullable=false)private Long patentId;
 @Column(name="claim_no",nullable=false)private Integer claimNo;@Column(name="version_no",nullable=false)private Integer versionNo;
 @Column(nullable=false)private String type;private Integer parentClaimNo;@Column(nullable=false,columnDefinition="LONGTEXT")private String content;
 @Column(columnDefinition="TEXT")private String technicalFeatures;@Column(columnDefinition="TEXT")private String supportReference;
 @Column(nullable=false)private Boolean currentVersion;@Column(nullable=false)private Long createdBy;private LocalDateTime createdAt;
 @PrePersist void onCreate(){if(versionNo==null)versionNo=1;if(currentVersion==null)currentVersion=true;createdAt=LocalDateTime.now();}
}
