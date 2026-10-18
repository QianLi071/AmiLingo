package com.amilingo.platform.module.career.service;

import com.amilingo.platform.module.career.entity.Application;
import com.amilingo.platform.module.career.entity.School;
import com.amilingo.platform.module.career.repository.ApplicationRepository;
import com.amilingo.platform.module.career.repository.SchoolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 院校与申请服务。 */
@Service
public class CareerService {

    private final SchoolRepository schoolRepository;
    private final ApplicationRepository applicationRepository;

    public CareerService(SchoolRepository schoolRepository,
                         ApplicationRepository applicationRepository) {
        this.schoolRepository = schoolRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional(readOnly = true)
    public List<School> listSchools() {
        return schoolRepository.findAll();
    }

    @Transactional(readOnly = true)
    public School getSchool(Long id) {
        return schoolRepository.findById(id).orElse(null);
    }

    @Transactional
    public School saveSchool(School school) {
        return schoolRepository.save(school);
    }

    @Transactional(readOnly = true)
    public List<Application> listApplications(Long userId) {
        return applicationRepository.findByUserIdOrderByDeadlineAsc(userId);
    }

    @Transactional
    public Application saveApplication(Long userId, Application application) {
        application.setUserId(userId);
        if (application.getStatus() == null || application.getStatus().isBlank()) {
            application.setStatus("DRAFT");
        }
        return applicationRepository.save(application);
    }

    @Transactional(readOnly = true)
    public List<School> matchSchools(Double targetTotal) {
        if (targetTotal == null) {
            return schoolRepository.findAllByOrderByRankingAsc();
        }
        return schoolRepository.findByMinTotalScoreLessThanEqualOrderByRankingAsc(targetTotal);
    }
}
