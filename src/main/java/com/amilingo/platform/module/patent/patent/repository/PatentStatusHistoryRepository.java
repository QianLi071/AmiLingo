package com.amilingo.platform.module.patent.repository;import com.amilingo.platform.module.patent.entity.PatentStatusHistory;import org.springframework.data.jpa.repository.JpaRepository;import java.util.List;
public interface PatentStatusHistoryRepository extends JpaRepository<PatentStatusHistory,Long>{List<PatentStatusHistory> findByPatentIdOrderByEffectiveDateDescIdDesc(Long patentId);}
