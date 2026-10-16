package com.amilingo.platform.module.lang.repository;

import com.amilingo.platform.module.lang.entity.PracticeAttempt;
import com.amilingo.platform.module.lang.enums.Section;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeAttemptRepository extends JpaRepository<PracticeAttempt, Long> {

    List<PracticeAttempt> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PracticeAttempt> findByUserIdAndSectionOrderByCreatedAtDesc(Long userId, Section section);

    List<PracticeAttempt> findTop3ByUserIdAndSectionOrderByCreatedAtDesc(Long userId, Section section);
}
