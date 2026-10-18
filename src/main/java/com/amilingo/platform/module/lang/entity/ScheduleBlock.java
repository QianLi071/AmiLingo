package com.amilingo.platform.module.lang.entity;

import com.amilingo.platform.common.util.Snowflake;
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
import java.time.LocalTime;

/**
 * 时间资源块：用户标记的独立时间段（24 小时分钟级）。
 * AI 排程时依据 blockType 标签决定该时段可分配的任务难度。
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "schedule_blocks", indexes = {
        @Index(name = "idx_block_user", columnList = "user_id"),
        @Index(name = "idx_block_user_day", columnList = "user_id,day_of_week")
})
public class ScheduleBlock {

    /** 时间块类别（AI 排程依据）。 */
    public enum BlockType {
        UNAVAILABLE, // 🔴 完全不可用：上课、会议，禁止安排任何学习任务
        FRAGMENT,    // 🟡 碎片琐事：通勤、吃饭，仅适配轻量短任务
        DEEP,        // 🟢 高效深度学习：安静连续长时段，安排高难度任务
        FREE,        // ⚪ 普通空闲：可安排中等强度学习任务
        REST         // 🟣 休息时段：强制休息，不分配任务
    }

    /** 循环规则。 */
    public enum RecurrenceType {
        WEEKLY, // 周循环
        ONCE    // 一次性临时事件
    }

    @Id
    @Column(name = "block_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 1=周一 … 7=周日。 */
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", length = 16)
    private BlockType blockType;

    @Column(name = "title", length = 128)
    private String title;

    /** 循环规则：周循环 / 一次性。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence", length = 16)
    private RecurrenceType recurrence;

    /** 生效起始日期（null = 立即生效）。 */
    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    /** 生效截止日期（null = 长期有效）。 */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** 一次性事件的具体日期（recurrence=ONCE 时必填）。 */
    @Column(name = "specific_date")
    private LocalDate specificDate;

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
