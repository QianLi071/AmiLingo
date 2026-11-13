package com.amilingo.platform.module.research.repository;

import com.amilingo.platform.module.research.entity.ResearchProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 科研项目台账数据访问层。
 * 提供按用户 ID 查询该用户所有科研项目的方法。
 */
@Repository
public interface ResearchProjectRepository extends JpaRepository<ResearchProject, Long> {

    /** 查询某用户的全部科研项目，按创建时间倒序 */
    List<ResearchProject> findByUserIdOrderByCreatedAtDesc(Long userId);
}
