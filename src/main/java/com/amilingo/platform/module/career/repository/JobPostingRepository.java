package com.amilingo.platform.module.career.repository;

import com.amilingo.platform.module.career.entity.JobPosting;
import com.amilingo.platform.module.career.enums.JobRegion;
import com.amilingo.platform.module.career.enums.JobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    @Query("""
            select job from JobPosting job
            where job.active = true
              and (:region is null or job.region = :region)
              and (:jobType is null or job.jobType = :jobType)
              and (:keyword is null
                   or lower(job.companyName) like lower(concat('%', :keyword, '%'))
                   or lower(job.positionTitle) like lower(concat('%', :keyword, '%'))
                   or lower(job.industry) like lower(concat('%', :keyword, '%')))
            order by case when job.applicationDeadline is null then 1 else 0 end,
                     job.applicationDeadline asc,
                     job.updatedAt desc
            """)
    List<JobPosting> searchActive(@Param("region") JobRegion region,
                                  @Param("jobType") JobType jobType,
                                  @Param("keyword") String keyword);

    Optional<JobPosting> findByIdAndActiveTrue(Long id);

    Optional<JobPosting> findByOfficialUrl(String officialUrl);
}
