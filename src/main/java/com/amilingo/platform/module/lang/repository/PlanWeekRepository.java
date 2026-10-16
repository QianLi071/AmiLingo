package com.amilingo.platform.module.lang.repository;

import com.amilingo.platform.module.lang.planner.PlanWeek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlanWeekRepository extends JpaRepository<PlanWeek, Long> {

    List<PlanWeek> findByCycleIdOrderByWeekIndex(Long cycleId);

    @Modifying
    @Query("DELETE FROM PlanWeek w WHERE w.cycleId = :cycleId")
    void deleteByCycleId(@Param("cycleId") Long cycleId);
}
