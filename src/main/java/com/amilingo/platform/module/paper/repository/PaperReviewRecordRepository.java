package com.amilingo.platform.module.paper.repository;
import com.amilingo.platform.module.paper.entity.PaperReviewRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PaperReviewRecordRepository extends JpaRepository<PaperReviewRecord, Long> {
    List<PaperReviewRecord> findByPaperIdOrderByStatusDateDescIdDesc(Long paperId);
}
