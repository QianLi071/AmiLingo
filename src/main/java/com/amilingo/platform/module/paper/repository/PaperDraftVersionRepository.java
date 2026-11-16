package com.amilingo.platform.module.paper.repository;
import com.amilingo.platform.module.paper.entity.PaperDraftVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaperDraftVersionRepository extends JpaRepository<PaperDraftVersion, Long> {
    List<PaperDraftVersion> findByPaperIdOrderByVersionNoDesc(Long paperId);
    Optional<PaperDraftVersion> findFirstByPaperIdOrderByVersionNoDesc(Long paperId);
    Optional<PaperDraftVersion> findByPaperIdAndCurrentVersionTrue(Long paperId);
}
