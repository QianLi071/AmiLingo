package com.amilingo.platform.module.paper.repository;
import com.amilingo.platform.module.paper.entity.PaperAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PaperAttachmentRepository extends JpaRepository<PaperAttachment,Long>{
    List<PaperAttachment> findByPaperIdAndStatusOrderByCreatedAtDesc(Long paperId,String status);
    List<PaperAttachment> findByPaperIdAndCategoryAndStatusOrderByCreatedAtDesc(Long paperId,String category,String status);
}
