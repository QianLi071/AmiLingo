package com.amilingo.platform.module.career.repository;

import com.amilingo.platform.module.career.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByUserIdOrderByDeadlineAsc(Long userId);
}
