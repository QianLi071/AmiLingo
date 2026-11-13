package com.amilingo.platform.module.research.repository;
import com.amilingo.platform.module.research.entity.TaskProgressLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TaskProgressLogRepository extends JpaRepository<TaskProgressLog, Long> {
    List<TaskProgressLog> findByTaskIdOrderByCreatedAtDesc(Long taskId);
}
