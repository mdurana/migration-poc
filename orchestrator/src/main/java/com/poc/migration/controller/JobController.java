package com.poc.migration.controller;

import com.poc.migration.model.Job;
import com.poc.migration.model.JobRequest;
import com.poc.migration.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/job")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<Job> createJob(@RequestBody JobRequest jobRequest) {
        try {
            Job newJob = jobService.createAndStartJob(jobRequest);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(newJob);
        } catch (Exception e) {
            // Basic error handling for the POC
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Job> getJobStatus(@PathVariable Long id) {
        return jobService.getJob(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}