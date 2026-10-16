package com.amilingo.platform.module.lang.repository;

import com.amilingo.platform.module.lang.entity.PracticeQuestion;
import com.amilingo.platform.module.lang.enums.ExamType;
import com.amilingo.platform.module.lang.enums.Section;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeQuestionRepository extends JpaRepository<PracticeQuestion, Long> {

    List<PracticeQuestion> findByExamTypeAndSection(ExamType examType, Section section);

    long countByExamTypeAndSection(ExamType examType, Section section);
}
