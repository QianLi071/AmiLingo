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
 * 月度周期单元：周期按自然月切分（与周期起止取交集）。
 * 月度配额是周计划的上限约束（各周配额之和等于月度配额）。
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "plan_months", indexes = {
        @Index(name = "idx_pm_cycle", columnList = "cycle_id")
})
public class PlanMonth {

    @Id
    @Column(name = "month_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** yyyy-MM。 */
    @Column(name = "month_key", nullable = false, length = 7)
    private String monthKey;

    /** 周期内第几月（从 0 开始）。 */
    @Column(name = "month_index", nullable = false)
    private Integer monthIndex;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 阶段：FOUNDATION/INTENSIVE/SPRINT。 */
    @Column(name = "stage", length = 16)
    private String stage;

    /** 当月可用学习总时长（分钟，按时间资源实际测算，扣除 🔴/🟣）。 */
    @Column(name = "available_minutes", nullable = false)
    private Integer availableMinutes;

    /** 其中 🟢 高效时段分钟数。 */
    @Column(name = "deep_minutes", nullable = false)
    private Integer deepMinutes;

    /** 其中 🟡 碎片时段分钟数。 */
    @Column(name = "fragment_minutes", nullable = false)
    private Integer fragmentMinutes;

    /** 月度工作量配额（分钟）。 */
    @Column(name = "quota_minutes", nullable = false)
    private Integer quotaMinutes;

    /** 分科配额 JSON：{"L":300,"R":450,...}。 */
    @Column(name = "quota_json", length = 1000)
    private String quotaJson;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = Snowflake.nextId();
        }
    }
}
