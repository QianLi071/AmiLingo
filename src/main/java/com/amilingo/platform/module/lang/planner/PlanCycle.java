package com.amilingo.platform.module.lang.planner;

import com.amilingo.platform.common.util.Snowflake;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 备考周期：用户自定义起止日期的一次长周期规划。
 * 生成后为 DRAFT（草稿，用户可拒绝/重新生成），采纳后为 ACCEPTED。
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "plan_cycles", indexes = {
        @Index(name = "idx_cycle_user", columnList = "user_id")
})
public class PlanCycle {

    /** DRAFT 草稿 / ACCEPTED 执行中 / ARCHIVED 已被新草稿替换（数据保留，仅作历史）。 */
    public enum Status { DRAFT, ACCEPTED, ARCHIVED }

    @Id
    @Column(name = "cycle_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "exam_type", length = 16)
    private String examType;

    /** 周期开始日期（用户定义）。 */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 周期结束日期（通常为考试日）。 */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "target_total")
    private Double targetTotal;

    @Column(name = "current_total")
    private Double currentTotal;

    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private String status = Status.DRAFT.name();

    /**
     * 自动调整级别（ddl-auto=update 自动加列，历史周期为 null 时按默认 SMART 处理）：
     * OFF  = 全手动，只出建议（合并初期行为）
     * SMART = 场景1（少量未完成迁移）自动执行；场景2/3 仅通知待确认（默认，推荐）
     * FULL = 场景1/2/3 全部自动执行（降档同周期最多 2 次，第 3 次仍转人工）
     */
    @Column(name = "auto_adjust_level", length = 8)
    @Builder.Default
    private String autoAdjustLevel = "SMART";

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
