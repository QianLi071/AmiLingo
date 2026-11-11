package com.amilingo.platform.module.patent.repository;import com.amilingo.platform.module.patent.entity.PatentDisclosure;import org.springframework.data.jpa.repository.JpaRepository;import java.util.Optional;
public interface PatentDisclosureRepository extends JpaRepository<PatentDisclosure,Long>{Optional<PatentDisclosure> findByPatentId(Long patentId);}
