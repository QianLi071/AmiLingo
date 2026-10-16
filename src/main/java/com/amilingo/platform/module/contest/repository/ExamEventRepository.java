package com.amilingo.platform.module.contest.repository;

import com.amilingo.platform.module.contest.entity.ExamEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamEventRepository extends JpaRepository<ExamEvent, Long> {

    List<ExamEvent> findAllByOrderByTestDateAsc();
}
