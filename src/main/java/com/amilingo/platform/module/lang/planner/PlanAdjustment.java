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

import java.time.LocalDateTime;

/**
 * 进度驱动的规划调整建议（场景1/2/3 + 防堆积预警）。
 * 由 ProgressAdjuster 生成、用户在前端确认后生效。
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "plan_adjustments", indexes = {
        @Index(name = "idx_adjust_cycle", columnList = "cycle_id"),
        @Index(name = "idx_adjust_status", columnList = "status")
})
public class PlanAdjustment {

    @Id
    @Column(name = "adjustment_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 场景类型：
     * SCENARIO1_MIGRATE（少量未完成，迁移到最近空闲切片）
     * SCENARIO2_REDUCE（连续低完成率，建议顺延+下调配额）
     * SCENARIO3_ACCELERATE（连续超额，建议增加本周任务量）
     * PILE_UP_WARNING（延期堆积预警）
     */
    @Column(name = "scenario", nullable = false, length = 32)
    private String scenario;

    /** 建议标题（前端卡片标题）。 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 建议详细说明（前端卡片正文）。 */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * 建议参数 JSON，例如：
     * SCENARIO1: {"taskIds":[123,456],"targetDate":"2026-09-28"}
     * SCENARIO2: {"delayTaskIds":[...],"newMonthQuota":480}
     * SCENARIO3: {"addMinutes":120,"section":"L"}
     */
    @Column(name = "payload", length = 2000)
    private String payload;

    /**
     * 状态：
     * PENDING（待用户处理）/ ACCEPTED（用户手动采纳并执行）/ REJECTED（用户拒绝）
     * / AUTO_EXECUTED（系统自动执行，可按快照撤销）/ REVOKED（自动调整已被用户撤销）/ EXPIRED。
     */
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private String status = "PENDING";

    /** 来源：AUTO（进度事件驱动的自动重排）/ MANUAL（评估后等待用户确认）。 */
    @Column(name = "source", nullable = false, length = 8)
    @Builder.Default
    private String source = "MANUAL";

    /**
     * 自动执行前的快照 JSON，供「撤销」一键回滚：
     * {"tasks":[{"id":..,"start":"..","end":"..","weekStart":"..","slotType":".."}],
     *  "weeks":[{"weekStart":"..","quotaMinutes":..}],"months":[{"monthKey":"..","quotaMinutes":..,"quotaJson":".."}]}
     */
    @Column(name = "before_snapshot", columnDefinition = "TEXT")
    private String beforeSnapshot;

    /** 触发时计算的当日完成率（0-100），用于前端展示上下文。 */
    @Column(name = "trigger_rate")
    private Integer triggerRate;

    /** 触发日期（用于判断是否已过期）。 */
    @Column(name = "trigger_date", nullable = false)
    private LocalDateTime triggerDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = true)
    private LocalDateTime createdAt;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = Snowflake.nextId();
        }
    }
}
