package com.poc.migration.controller;

import com.poc.migration.model.Job;
import com.poc.migration.model.JobRequest;
import com.poc.migration.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for migration job operations.
 * Exception handling is centralized in GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/job")
@RequiredArgsConstructor
@Slf4j
public class JobController {

    private final JobService jobService;

    /**
     * Create and start a new migration job.
     * Returns immediately with job ID; migration runs asynchronously.
     */
    @PostMapping
    public ResponseEntity<Job> createJob(@Valid @RequestBody JobRequest jobRequest) {
        log.info("Received job creation request: {}", jobRequest.getJobName());
        
        Job newJob = jobService.createAndStartJob(jobRequest);
        
        log.info("Job created with ID: {}", newJob.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(newJob);
    }

    /**
     * Get the status of a migration job.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Job> getJobStatus(@PathVariable Long id) {
        log.debug("Getting status for job ID: {}", id);
        
        return jobService.getJob(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}