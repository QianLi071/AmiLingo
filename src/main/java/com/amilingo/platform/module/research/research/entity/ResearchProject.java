package com.amilingo.platform.module.research.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 科研项目台账 — 数据库实体，对应表 research_project。
 * 每条记录代表一个用户的一个科研项目。
 */
@Entity
@Table(name = "research_project")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResearchProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户 ID（由前端/Header 传入，MVP 阶段不做 Auth 校验） */
    @Column(nullable = false)
    private Long userId;

    /** 项目名称 */
    @Column(nullable = false)
    private String projectName;

    /** 项目简介与研究目标 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 研究方向/领域 */
    private String researchField;

    /** 本人角色，如：主要参与者、PI助理、项目负责人 */
    private String role;

    /** 指导教师姓名 */
    private String supervisor;

    /** 所属实验室 */
    private String laboratory;

    /** 项目起始日期 */
    private LocalDate startDate;

    /** 项目结束日期（进行中时为 null） */
    private LocalDate endDate;

    /**
     * 项目状态：ONGOING=进行中 / COMPLETED=已完成 / SUSPENDED=暂停
     */
    @Column(nullable = false)
    private String status;

    /** 立项审批状态：DRAFT / PENDING / APPROVED / REJECTED */
    @Column(nullable = false)
    private String applicationStatus;

    /** 立项审批意见 */
    @Column(columnDefinition = "TEXT")
    private String approvalOpinion;

    /** 审批人用户 ID */
    private Long approvedBy;

    /** 提交审批时间 */
    private LocalDateTime submittedAt;

    /** 审批完成时间 */
    private LocalDateTime approvedAt;

    /** 项目总体完成百分比，范围 0-100 */
    @Column(nullable = false)
    private Integer progress;

    /** 项目成果描述 */
    @Column(columnDefinition = "TEXT")
    private String outcome;

    /**
     * 附件列表，以英文逗号分隔的文件名/路径字符串存储。
     * 示例："report.pdf,data.xlsx"
     */
    @Column(columnDefinition = "TEXT")
    private String attachments;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (status == null || status.isBlank()) status = "ONGOING";
        if (applicationStatus == null || applicationStatus.isBlank()) applicationStatus = "DRAFT";
        if (progress == null) progress = 0;
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
