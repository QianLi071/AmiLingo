package com.amilingo.platform.module.lang.repository;

import com.amilingo.platform.module.lang.entity.ScheduleBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScheduleBlockRepository extends JpaRepository<ScheduleBlock, Long> {

    List<ScheduleBlock> findByUserId(Long userId);

    List<ScheduleBlock> findByUserIdAndDayOfWeek(Long userId, Integer dayOfWeek);

    /** 批量删除（不加载实体，避免旧枚举值映射失败）。 */
    @Modifying
    @Query("DELETE FROM ScheduleBlock b WHERE b.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
