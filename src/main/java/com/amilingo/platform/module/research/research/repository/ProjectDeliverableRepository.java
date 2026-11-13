package com.amilingo.platform.module.research.repository;
import com.amilingo.platform.module.research.entity.ProjectDeliverable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProjectDeliverableRepository extends JpaRepository<ProjectDeliverable, Long> {
    List<ProjectDeliverable> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<ProjectDeliverable> findByProjectIdAndTypeOrderByCreatedAtDesc(Long projectId, String type);
}
