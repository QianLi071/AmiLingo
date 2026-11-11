package com.amilingo.platform.module.research.repository;
import com.amilingo.platform.module.research.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
    List<ProjectMember> findByProjectIdOrderByCreatedAtAsc(Long projectId);
    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);
    boolean existsByProjectIdAndUserIdAndStatus(Long projectId, Long userId, String status);
}
