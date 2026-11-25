package com.amilingo.platform.module.paper.repository;
import com.amilingo.platform.module.paper.entity.PaperAuthor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaperAuthorRepository extends JpaRepository<PaperAuthor,Long>{
    List<PaperAuthor> findByPaperIdOrderByAuthorOrderAsc(Long paperId);
    boolean existsByPaperIdAndAuthorOrder(Long paperId,Integer authorOrder);
}
