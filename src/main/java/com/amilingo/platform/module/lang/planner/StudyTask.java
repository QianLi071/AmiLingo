package com.amilingo.platform.module.lang.planner;

import com.amilingo.platform.common.util.Snowflake;
import com.amilingo.platform.module.lang.enums.Section;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习任务：排程引擎产出、落到具体时间切片的最小学习单元。
 * 同一批任务共享 {@code planId}；长周期规划引擎产出的任务还带有 cycleId/monthKey/weekStart。
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "study_tasks", indexes = {
        @Index(name = "idx_task_user", columnList = "user_id"),
        @Index(name = "idx_task_plan", columnList = "plan_id"),
        @Index(name = "idx_task_start", columnList = "scheduled_start")
})
public class StudyTask {

    @Id
    @Column(name = "task_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 16)
    private Section section;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "scheduled_start", nullable = false)
    private LocalDateTime scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private LocalDateTime scheduledEnd;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    /** 阶段：FOUNDATION / INTENSIVE / SPRINT。 */
    @Column(name = "stage", length = 16)
    private String stage;

    /** 任务类型：词汇/精听/精读/写作/口语/模考 等。 */
    @Column(name = "task_type", length = 32)
    private String taskType;

    @Column(name = "done", nullable = false)
    @Builder.Default
    private boolean done = false;

    /* ===== 长周期规划引擎字段（ddl-auto=update 自动加列，旧计划任务为 null） ===== */

    /** 所属备考周期。 */
    @Column(name = "cycle_id")
    private Long cycleId;

    /** 所属月度单元 yyyy-MM。 */
    @Column(name = "month_key", length = 7)
    private String monthKey;

    /** 所属周的周一日期。 */
    @Column(name = "week_start")
    private LocalDate weekStart;

    /** 来源时间切片类型：DEEP/FREE/FRAGMENT。 */
    @Column(name = "slot_type", length = 16)
    private String slotType;

    /** 任务难度：HIGH/MEDIUM/LIGHT。 */
    @Column(name = "difficulty", length = 16)
    private String difficulty;

    /** 该时间切片绑定的学习目标。 */
    @Column(name = "objective", length = 300)
    private String objective;

    /** 进度状态：TODO/PARTIAL/DONE（对接任务模块进度回调）。 */
    @Column(name = "progress_status", length = 16)
    @Builder.Default
    private String progressStatus = "TODO";

    /** 实际完成工作量（分钟），由任务模块回调写入。 */
    @Column(name = "actual_minutes")
    private Integer actualMinutes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = Snowflake.nextId();
        }
    }
}
