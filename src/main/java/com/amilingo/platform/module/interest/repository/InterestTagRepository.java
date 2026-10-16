package com.amilingo.platform.module.interest.repository;

import com.amilingo.platform.module.interest.entity.InterestTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterestTagRepository extends JpaRepository<InterestTag, Long> {

    List<InterestTag> findByCategory(String category);
}
