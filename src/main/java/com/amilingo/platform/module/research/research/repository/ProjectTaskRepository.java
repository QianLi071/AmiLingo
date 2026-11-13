package com.amilingo.platform.module.research.repository;
import com.amilingo.platform.module.research.entity.ProjectTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProjectTaskRepository extends JpaRepository<ProjectTask, Long> {
    List<ProjectTask> findByProjectIdOrderByDueDateAscIdAsc(Long projectId);
    List<ProjectTask> findByAssigneeUserIdOrderByDueDateAscIdAsc(Long assigneeUserId);
    List<ProjectTask> findByMilestoneIdOrderByDueDateAscIdAsc(Long milestoneId);
}
