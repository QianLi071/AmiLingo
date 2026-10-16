package com.amilingo.platform.module.lang.repository;

import com.amilingo.platform.module.lang.entity.ExamGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamGoalRepository extends JpaRepository<ExamGoal, Long> {

    List<ExamGoal> findByUserId(Long userId);

    Optional<ExamGoal> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
