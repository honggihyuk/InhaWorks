package com.mysite.sbb.batch.repository;

import com.mysite.sbb.batch.constant.JobStatus;
import com.mysite.sbb.batch.entity.JobExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {

    Optional<JobExecution> findByJobNameAndJobKey(String jobName, String jobKey);

    List<JobExecution> findByStatus(JobStatus status);

    List<JobExecution> findTop50ByOrderByIdDesc();

    long countByStatus(JobStatus status);
}
