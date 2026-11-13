package com.amilingo.platform.module.research.repository;
import com.amilingo.platform.module.research.entity.ProjectMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestone, Long> {
    List<ProjectMilestone> findByProjectIdOrderBySortOrderAscIdAsc(Long projectId);
}
