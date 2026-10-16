package com.amilingo.platform.module.lang.repository;

import com.amilingo.platform.module.lang.planner.PlanAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlanAdjustmentRepository extends JpaRepository<PlanAdjustment, Long> {

    List<PlanAdjustment> findByCycleIdAndStatusOrderByCreatedAtDesc(Long cycleId, String status);

    List<PlanAdjustment> findByCycleIdOrderByCreatedAtDesc(Long cycleId);

    /** 最近若干条指定状态的调整（前端展示待处理建议 + 可撤销的自动调整记录）。 */
    List<PlanAdjustment> findTop20ByCycleIdAndStatusInOrderByCreatedAtDesc(Long cycleId,
                                                                           List<String> statuses);

    /** 用户是否已存在同类型 PENDING 建议（避免同场景重复刷）。 */
    boolean existsByCycleIdAndScenarioAndStatus(Long cycleId, String scenario, String status);

    /** 统计某周期内指定场景/来源/状态的调整数（自动降档次数限制用）。 */
    long countByCycleIdAndScenarioAndSourceAndStatus(Long cycleId, String scenario,
                                                     String source, String status);

    @Modifying
    @Query("DELETE FROM PlanAdjustment a WHERE a.cycleId = :cycleId")
    void deleteByCycleId(@Param("cycleId") Long cycleId);
}
