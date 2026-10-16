package com.amilingo.platform.module.lang.repository;

import com.amilingo.platform.module.lang.planner.StudyTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudyTaskRepository extends JpaRepository<StudyTask, Long> {

    List<StudyTask> findByUserId(Long userId);

    /** 旧版 AI 排程遗留任务（未归属任何长周期）。 */
    @Query("SELECT t FROM StudyTask t WHERE t.userId = :userId AND t.cycleId IS NULL")
    List<StudyTask> findLegacyByUserId(@Param("userId") Long userId);

    List<StudyTask> findByUserIdAndPlanId(Long userId, Long planId);

    List<StudyTask> findByCycleIdOrderByScheduledStart(Long cycleId);

    Optional<StudyTask> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);

    /** 仅清理旧版引擎遗留任务，禁止误删已归属周期的切片任务。 */
    @Modifying
    @Query("DELETE FROM StudyTask t WHERE t.userId = :userId AND t.cycleId IS NULL")
    void deleteLegacyByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM StudyTask t WHERE t.cycleId = :cycleId")
    void deleteByCycleId(@Param("cycleId") Long cycleId);
}
