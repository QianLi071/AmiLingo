package com.amilingo.platform.module.lang.entity;

import com.amilingo.platform.common.util.Snowflake;
import com.amilingo.platform.module.lang.enums.ExamType;
import com.amilingo.platform.module.lang.enums.Section;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
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
 * 练习尝试 / 做题流水：记录每次听力/阅读/写作/口语练习的作答与评分。
 * 是薄弱点分析、历史趋势、错题闭环的数据来源。
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "practice_attempts", indexes = {
        @Index(name = "idx_att_user", columnList = "user_id"),
        @Index(name = "idx_att_user_section", columnList = "user_id,section"),
        @Index(name = "idx_att_question", columnList = "question_id")
})
public class PracticeAttempt {

    @Id
    @Column(name = "attempt_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "question_id")
    private Long questionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 16)
    private ExamType examType;

    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 16)
    private Section section;

    /** 用户作答（选择题答案 / 作文正文 / 口语转写）。 */
    @Lob
    @Column(name = "user_answer", columnDefinition = "TEXT")
    private String userAnswer;

    @Column(name = "overall_score")
    private Double overall;

    /** 分项分 JSON，写作 {"TR":..,"CC":..}，口语 {"FC":..,...}。 */
    @Lob
    @Column(name = "scores", columnDefinition = "TEXT")
    private String scores;

    @Lob
    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

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
