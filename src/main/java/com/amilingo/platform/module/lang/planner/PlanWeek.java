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

import java.time.LocalDate;

/**
 * 周计划单元：周一至周日（与周期起止取交集），归属于其周一开始日所在的自然月。
 * 周基准配额由月度配额按各周可用时长比例下放，各周配额之和严格等于月度配额。
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "plan_weeks", indexes = {
        @Index(name = "idx_pw_cycle", columnList = "cycle_id")
})
public class PlanWeek {

    @Id
    @Column(name = "week_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "month_key", nullable = false, length = 7)
    private String monthKey;

    /** 周期内第几周（从 0 开始）。 */
    @Column(name = "week_index", nullable = false)
    private Integer weekIndex;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "week_end", nullable = false)
    private LocalDate weekEnd;

    /** 本周可用时长（分钟）。 */
    @Column(name = "available_minutes", nullable = false)
    private Integer availableMinutes;

    /** 本周任务基准配额（分钟）。 */
    @Column(name = "quota_minutes", nullable = false)
    private Integer quotaMinutes;

    /** 实际排入的任务总量（分钟）。 */
    @Column(name = "allocated_minutes", nullable = false)
    @Builder.Default
    private Integer allocatedMinutes = 0;

    /** 分科配额 JSON：{"L":300,...}。 */
    @Column(name = "quota_json", length = 1000)
    private String quotaJson;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = Snowflake.nextId();
        }
    }
}
