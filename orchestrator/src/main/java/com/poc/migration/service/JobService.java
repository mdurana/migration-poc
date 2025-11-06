package com.poc.migration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.migration.model.Job;
import com.poc.migration.model.JobRepository;
import com.poc.migration.model.JobRequest;
import com.poc.migration.model.JobStatus;
import com.poc.migration.orchestration.MigrationOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for managing migration jobs.
 * Refactored to use MigrationOrchestrator for lifecycle management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository jobRepository;
    private final MigrationOrchestrator migrationOrchestrator;
    private final ObjectMapper objectMapper;

    public Optional<Job> getJob(Long id) {
        return jobRepository.findById(id);
    }

    @Transactional
    public Job createAndStartJob(JobRequest jobRequest) {
        try {
            log.info("Creating job: {}", jobRequest.getJobName());

            // 1. Create and save the initial job state
            Job job = new Job();
            job.setJobName(jobRequest.getJobName());
            job.setStatus(JobStatus.PENDING);
            job.setJobRequestJson(objectMapper.writeValueAsString(jobRequest));
            Job savedJob = jobRepository.save(job);

            // 2. Start the async execution
            runMigrationLifecycle(savedJob.getId(), jobRequest);

            return savedJob;
            
        } catch (Exception e) {
            log.error("Failed to create job: {}", e.getMessage(), e);
            throw new com.poc.migration.exception.MigrationException(
                "Failed to create and start job: " + jobRequest.getJobName(), e
            );
        }
    }

    /**
     * Run the migration lifecycle asynchronously.
     * Uses MigrationOrchestrator to execute phases in sequence.
     */
    @Async
    public void runMigrationLifecycle(Long jobId, JobRequest request) {
        log.info("[Job-{}] Migration lifecycle started", jobId);
        
        try {
            // Execute migration through orchestrator
            JobStatus finalStatus = migrationOrchestrator.executeMigrationLifecycle(
                jobId,
                request,
                (status, error) -> updateStatus(jobId, status, error)
            );
            
            // Update final status
            updateStatus(jobId, finalStatus, null);
            
        } catch (Exception e) {
            log.error("[Job-{}] Unexpected error in lifecycle execution: {}", jobId, e.getMessage(), e);
            updateStatus(jobId, JobStatus.DATA_FAILED, e.getMessage());
        }
    }

    /**
     * Helper to update job status transactionally.
     */
    @Transactional
    public void updateStatus(Long jobId, JobStatus status, String error) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
        job.setStatus(status);
        job.setLastError(error);
        jobRepository.save(job);
        log.info("[Job-{}] Status updated to: {}", jobId, status);
    }

}