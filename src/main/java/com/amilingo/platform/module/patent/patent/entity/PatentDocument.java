package com.amilingo.platform.module.patent.entity;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
/** 专利申请、审查、授权材料的文件元数据。 */
@Entity @Table(name="patent_document") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatentDocument{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;@Column(nullable=false)private Long patentId;private Long relatedRecordId;
 @Column(nullable=false)private String category;@Column(nullable=false)private String fileName;private String contentType;private Long fileSize;
 @Column(nullable=false,unique=true)private String storageKey;private String fileUrl;private String fileHash;@Column(nullable=false)private Integer versionNo;
 @Column(nullable=false)private Long uploadedBy;@Column(nullable=false)private String status;private LocalDateTime createdAt;
 @PrePersist void onCreate(){if(versionNo==null)versionNo=1;if(status==null)status="ACTIVE";createdAt=LocalDateTime.now();}
}
