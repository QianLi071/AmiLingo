package com.amilingo.platform.module.lang.repository;

import com.amilingo.platform.module.lang.planner.PlanCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlanCycleRepository extends JpaRepository<PlanCycle, Long> {

    List<PlanCycle> findByUserId(Long userId);

    Optional<PlanCycle> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    /** 查「当前周期」：排除已归档（ARCHIVED）的历史周期，取最新的草稿或执行中周期。 */
    Optional<PlanCycle> findFirstByUserIdAndStatusNotOrderByCreatedAtDesc(Long userId, String status);

    Optional<PlanCycle> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("DELETE FROM PlanCycle c WHERE c.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
