package com.amilingo.platform.module.lang.repository;

import com.amilingo.platform.module.lang.planner.PlanMonth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlanMonthRepository extends JpaRepository<PlanMonth, Long> {

    List<PlanMonth> findByCycleIdOrderByMonthIndex(Long cycleId);

    @Modifying
    @Query("DELETE FROM PlanMonth m WHERE m.cycleId = :cycleId")
    void deleteByCycleId(@Param("cycleId") Long cycleId);
}
