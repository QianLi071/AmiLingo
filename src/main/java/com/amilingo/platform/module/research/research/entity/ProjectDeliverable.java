package com.amilingo.platform.module.research.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 项目成果、文件或专利技术交底元数据。 */
@Entity
@Table(name = "research_deliverable")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectDeliverable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false) private Long creatorUserId;
    @Column(nullable = false) private String type;
    @Column(nullable = false) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(columnDefinition = "TEXT") private String technicalBackground;
    @Column(columnDefinition = "TEXT") private String technicalSolution;
    @Column(columnDefinition = "TEXT") private String innovations;
    private LocalDate completionDate;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String storageKey;
    private String fileUrl;
    /** 与专利模块对接后回填其专利 ID。 */
    private Long linkedPatentId;
    @Column(nullable = false) private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist void onCreate() {
        if (status == null || status.isBlank()) status = "DRAFT";
        createdAt = updatedAt = LocalDateTime.now();
    }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
