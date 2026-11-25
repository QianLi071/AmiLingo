package com.amilingo.platform.module.paper.repository;
import com.amilingo.platform.module.paper.entity.PublicationVenue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PublicationVenueRepository extends JpaRepository<PublicationVenue,Long>{
    List<PublicationVenue> findByOwnerUserIdOrderByNameAsc(Long ownerUserId);
    List<PublicationVenue> findByOwnerUserIdAndTypeOrderByNameAsc(Long ownerUserId,String type);
}
