package com.amilingo.platform.module.patent.repository;import com.amilingo.platform.module.patent.entity.PatentFee;import org.springframework.data.jpa.repository.JpaRepository;import java.util.List;
public interface PatentFeeRepository extends JpaRepository<PatentFee,Long>{List<PatentFee> findByPatentIdOrderByDueDateAscIdAsc(Long patentId);}
