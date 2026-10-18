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

/** 练习题题库（听力/阅读选择题、写作题目等）。 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "practice_questions", indexes = {
        @Index(name = "idx_q_exam_section", columnList = "exam_type,section")
})
public class PracticeQuestion {

    @Id
    @Column(name = "question_id", updatable = false, nullable = false, unique = true)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 16)
    private ExamType examType;

    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 16)
    private Section section;

    /** 题干 / 题目内容。 */
    @Lob
    @Column(name = "stem", nullable = false)
    private String stem;

    /** 选项 JSON，如 ["A. ...","B. ..."]；写作题可为 null。 */
    @Lob
    @Column(name = "options")
    private String options;

    /** 正确答案（选择题为选项字母；写作题为参考范文/要点）。 */
    @Lob
    @Column(name = "answer")
    private String answer;

    /** 难度分级：基础/中等/冲刺。 */
    @Column(name = "level", length = 16)
    private String level;

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
