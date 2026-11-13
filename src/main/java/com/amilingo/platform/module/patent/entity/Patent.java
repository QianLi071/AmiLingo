package com.amilingo.platform.module.patent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 专利台账 — 数据库实体，对应表 patent。
 * 每条记录代表一项用户参与的专利。
 */
@Entity
@Table(name = "patent")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Patent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户 ID */
    @Column(nullable = false)
    private Long userId;

    /** 专利名称 */
    @Column(nullable = false)
    private String name;

    /**
     * 专利类型：INVENTION=发明专利 / UTILITY=实用新型 / DESIGN=外观设计
     */
    @Column(nullable = false)
    private String type;

    /** 申请号（申请阶段填写） */
    private String applicationNumber;

    /** 授权号（授权后填写） */
    private String grantNumber;

    /** 发明人顺序（第几发明人，1 = 第一发明人） */
    private Integer inventorOrder;

    /**
     * 状态：APPLIED=已申请 / SUBSTANTIVE_EXAM=实审中 /
     *       GRANTED=已授权 / REJECTED=已驳回
     */
    @Column(nullable = false)
    private String status;

    /** 申请日期 */
    private LocalDate applicationDate;

    /** 授权日期（授权后填写） */
    private LocalDate grantDate;

    /**
     * 附件列表，逗号分隔的文件名/路径字符串。
     * 示例："patent_application.pdf,tech_desc.docx"
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
