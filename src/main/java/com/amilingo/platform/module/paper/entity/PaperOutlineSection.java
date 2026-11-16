package com.amilingo.platform.module.paper.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 论文大纲章节，支持树形父子章节和排序。 */
@Entity @Table(name = "paper_outline_section")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaperOutlineSection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long paperId;
    private Long parentId;
    @Column(nullable = false) private String title;
    @Column(columnDefinition = "TEXT") private String summary;
    @Column(nullable = false) private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist void onCreate() { if (sortOrder == null) sortOrder = 0; createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
