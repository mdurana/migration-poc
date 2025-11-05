package com.poc.migration.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {
    
    /**
     * Find a job by its name.
     * Note: Multiple jobs can have the same name.
     */
    List<Job> findByJobName(String jobName);
    
    /**
     * Find the most recent job with a given name.
     */
    Optional<Job> findFirstByJobNameOrderByCreatedAtDesc(String jobName);
    
    /**
     * Find all jobs with a specific status.
     */
    List<Job> findByStatus(JobStatus status);
    
    /**
     * Find jobs created after a certain date.
     */
    List<Job> findByCreatedAtAfter(LocalDateTime date);
    
    /**
     * Find running jobs (not terminal states).
     */
    @Query("SELECT j FROM Job j WHERE j.status NOT IN " +
        "('DONE', 'SCHEMA_GENERATE_FAILED', 'SCHEMA_NORMALIZE_FAILED', " +
        "'SCHEMA_FAILED', 'DATA_CONFIG_FAILED', 'DATA_FAILED', " +
        "'VALIDATION_FAILED', 'COMMIT_FAILED')")
    List<Job> findRunningJobs();
    
    /**
     * Count jobs by status.
     */
    long countByStatus(JobStatus status);
}