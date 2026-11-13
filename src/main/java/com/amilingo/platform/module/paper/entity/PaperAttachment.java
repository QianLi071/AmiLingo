package com.amilingo.platform.module.paper.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 论文材料的存储元数据；实际二进制由主框架文件服务管理。 */
@Entity @Table(name="paper_attachment")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaperAttachment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long paperId;
    private Long relatedRecordId;
    @Column(nullable=false) private String category;
    @Column(nullable=false) private String fileName;
    private String contentType;
    private Long fileSize;
    @Column(nullable=false, unique=true) private String storageKey;
    private String fileUrl;
    private String fileHash;
    private Integer versionNo;
    @Column(nullable=false) private Long uploadedBy;
    @Column(nullable=false) private String status;
    private LocalDateTime createdAt;
    @PrePersist void onCreate(){ if(versionNo==null)versionNo=1; if(status==null)status="ACTIVE"; createdAt=LocalDateTime.now(); }
}
