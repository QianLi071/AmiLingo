package com.amilingo.platform.module.research.repository;
import com.amilingo.platform.module.research.entity.AdvisorRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AdvisorRecordRepository extends JpaRepository<AdvisorRecord, Long> {
    List<AdvisorRecord> findByProjectIdOrderByGuidanceDateDescIdDesc(Long projectId);
}
