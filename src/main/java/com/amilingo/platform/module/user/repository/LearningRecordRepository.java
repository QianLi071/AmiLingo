package com.amilingo.platform.module.user.repository;

import com.amilingo.platform.module.user.entity.user.LearningRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningRecordRepository extends JpaRepository<LearningRecord, Long> {

    List<LearningRecord> findByUserId(Long userId);
}
