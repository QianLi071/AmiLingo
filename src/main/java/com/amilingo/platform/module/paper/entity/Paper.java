package com.amilingo.platform.module.paper.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 论文台账 — 数据库实体，对应表 paper。
 * 每条记录代表一篇用户参与的论文。
 */
@Entity
@Table(name = "paper")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Paper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户 ID */
    @Column(nullable = false)
    private Long userId;

    /** 论文标题 */
    @Column(nullable = false)
    private String title;

    /** 作者顺序（第几作者，1 = 第一作者） */
    private Integer authorOrder;

    /** 发表期刊或会议名称 */
    private String journal;

    /**
     * 级别，如：SCI Q1 / SCI Q2 / EI / CCF-A / CCF-B / 核心期刊 / 普通期刊
     */
    private String level;

    /**
     * 状态：WRITING=撰写中 / SUBMITTED=已投稿 / ACCEPTED=已录用 / PUBLISHED=已见刊
     */
    @Column(nullable = false)
    private String status;

    /** 投稿日期 */
    private LocalDate submissionDate;

    /** 录用日期 */
    private LocalDate acceptanceDate;

    /** 见刊/出版日期 */
    private LocalDate publicationDate;

    /** DOI 标识符 */
    private String doi;

    /**
     * 附件列表，逗号分隔的文件名/路径字符串。
     * 示例："manuscript_v3.pdf,review_comments.docx"
     */
    @Column(columnDefinition = "TEXT")
    private String attachments;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
