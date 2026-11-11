package com.amilingo.platform.module.paper.repository;
import com.amilingo.platform.module.paper.entity.PaperSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaperSubmissionRepository extends JpaRepository<PaperSubmission, Long> {
    List<PaperSubmission> findByPaperIdOrderByRoundNoDesc(Long paperId);
    Optional<PaperSubmission> findFirstByPaperIdOrderByRoundNoDesc(Long paperId);
}
