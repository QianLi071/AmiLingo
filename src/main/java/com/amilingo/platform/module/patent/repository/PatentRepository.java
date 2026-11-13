package com.amilingo.platform.module.patent.repository;

import com.amilingo.platform.module.patent.entity.Patent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 专利台账数据访问层。
 * 提供按用户 ID 查询该用户所有专利的方法。
 */
@Repository
public interface PatentRepository extends JpaRepository<Patent, Long> {

    /** 查询某用户的全部专利，按创建时间倒序 */
    List<Patent> findByUserIdOrderByCreatedAtDesc(Long userId);
}
