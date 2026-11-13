package com.amilingo.platform.module.research.repository;
import com.amilingo.platform.module.research.entity.ProjectJoinApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProjectJoinApplicationRepository extends JpaRepository<ProjectJoinApplication, Long> {
    List<ProjectJoinApplication> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<ProjectJoinApplication> findByApplicantUserIdOrderByCreatedAtDesc(Long applicantUserId);
    boolean existsByProjectIdAndApplicantUserIdAndStatus(Long projectId, Long applicantUserId, String status);
}
