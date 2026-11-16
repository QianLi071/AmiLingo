package com.amilingo.platform.module.paper.repository;
import com.amilingo.platform.module.paper.entity.PaperFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PaperFeedbackRepository extends JpaRepository<PaperFeedback, Long> {
    List<PaperFeedback> findByPaperIdOrderByFeedbackDateDescIdDesc(Long paperId);
}
