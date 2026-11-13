package com.amilingo.platform.module.paper.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 论文草稿/修改稿的不可变版本快照。 */
@Entity @Table(name = "paper_draft_version", uniqueConstraints =
        @UniqueConstraint(name = "uk_paper_version", columnNames = {"paper_id", "version_no"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaperDraftVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "paper_id", nullable = false) private Long paperId;
    @Column(name = "version_no", nullable = false) private Integer versionNo;
    @Column(nullable = false) private String versionType;
    @Column(columnDefinition = "LONGTEXT") private String content;
    private Integer wordCount;
    @Column(columnDefinition = "TEXT") private String changeNote;
    @Column(nullable = false) private Long createdBy;
    @Column(nullable = false) private Boolean currentVersion;
    private LocalDateTime createdAt;
    @PrePersist void onCreate() {
        if (wordCount == null) wordCount = 0;
        if (currentVersion == null) currentVersion = true;
        createdAt = LocalDateTime.now();
    }
}
