package com.amilingo.platform.module.lang.repository;

import com.amilingo.platform.module.lang.entity.ScoreTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScoreTargetRepository extends JpaRepository<ScoreTarget, Long> {

    List<ScoreTarget> findByUserId(Long userId);

    List<ScoreTarget> findByUserIdAndGoalId(Long userId, Long goalId);
}
