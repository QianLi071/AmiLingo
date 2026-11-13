package com.amilingo.platform.module.paper.repository;
import com.amilingo.platform.module.paper.entity.PaperCollaborator;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaperCollaboratorRepository extends JpaRepository<PaperCollaborator,Long>{
    Optional<PaperCollaborator> findByPaperIdAndUserId(Long paperId,Long userId);
    List<PaperCollaborator> findByPaperIdOrderByCreatedAtAsc(Long paperId);
    List<PaperCollaborator> findByUserIdAndStatus(Long userId,String status);
}
