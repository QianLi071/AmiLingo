package com.amilingo.platform.module.paper.repository;
import com.amilingo.platform.module.paper.entity.PaperStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PaperStatusHistoryRepository extends JpaRepository<PaperStatusHistory, Long> {
    List<PaperStatusHistory> findByPaperIdOrderByCreatedAtDesc(Long paperId);
}
