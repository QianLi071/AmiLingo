package com.amilingo.platform.module.paper.repository;
import com.amilingo.platform.module.paper.entity.PaperOutlineSection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PaperOutlineSectionRepository extends JpaRepository<PaperOutlineSection, Long> {
    List<PaperOutlineSection> findByPaperIdOrderBySortOrderAscIdAsc(Long paperId);
}
