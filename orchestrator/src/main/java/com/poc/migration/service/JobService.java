package com.poc.migration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.migration.executor.DataExecutor;
import com.poc.migration.executor.SchemaExecutor;
import com.poc.migration.executor.ValidationExecutor;
import com.poc.migration.model.Job;
import com.poc.migration.model.JobRepository;
import com.poc.migration.model.JobRequest;
import com.poc.migration.model.JobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository jobRepository;
    private final SchemaExecutor schemaExecutor;
    private final DataExecutor dataExecutor;
    private final ValidationExecutor validationExecutor;
    private final ObjectMapper objectMapper; // For serializing the request

    @Value("${migration.schema.output-dir}")
    private String schemaOutputDir;

    public Optional<Job> getJob(Long id) {
        return jobRepository.findById(id);
    }

    @Transactional
    public Job createAndStartJob(JobRequest jobRequest) throws Exception {
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
    }

    @Async // Run the entire lifecycle in a separate thread
    public void runMigrationLifecycle(Long jobId, JobRequest request) {
        log.info("[Job-{}] Lifecycle started.", jobId);

        // Define a unique path for the generated changelog
        // e.g., /app/generated-schema/job-1-changelog.xml
        String generatedChangelogPath = schemaOutputDir + "job-" + jobId + "-changelog.xml";

        try {
            // === 1. SCHEMA GENERATION ===
            updateStatus(jobId, JobStatus.SCHEMA_GENERATING, null);
            schemaExecutor.generateChangelog(request, generatedChangelogPath);
            log.info("[Job-{}] Schema generated successfully to {}.", jobId, generatedChangelogPath);

            // === 2. SCHEMA APPLY ===
            updateStatus(jobId, JobStatus.SCHEMA_APPLYING, null);
            schemaExecutor.applyChangelog(request, generatedChangelogPath);
            log.info("[Job-{}] Schema applied successfully to target.", jobId);

            // === 3. DATA CONFIGURATION ===
            updateStatus(jobId, JobStatus.DATA_CONFIGURING, null);
            dataExecutor.registerSourceAndTarget(request);
            dataExecutor.createMigrationJob(jobId, request);
            log.info("[Job-{}] Data sources configured.", jobId);

            // === 4. DATA EXECUTION (Inventory + CDC) ===
            updateStatus(jobId, JobStatus.DATA_RUNNING, null);
            dataExecutor.startMigrationJob(jobId);
            // This is the long-polling part.
            dataExecutor.monitorJobUntilCdc(jobId);
            log.info("[Job-{}] CDC is running and in sync.", jobId);

            // === 5. VALIDATION ===
            updateStatus(jobId, JobStatus.VALIDATING, null);
            boolean isValid = validationExecutor.validateRowCounts(request);
            if (!isValid) {
                throw new RuntimeException("Validation Failed: Row counts do not match.");
            }
            log.info("[Job-{}] Validation passed.", jobId);

            // === 6. DONE ===
            // In a real tool, we would wait for cutover. Here, we just stop.
            dataExecutor.stopMigrationJob(jobId);
            log.info("[Job-{}] Job stopped.", jobId);
            updateStatus(jobId, JobStatus.DONE, null);
            log.info("[Job-{}] Lifecycle COMPLETE.", jobId);

        } catch (Exception e) {
            log.error("[Job-{}] Lifecycle FAILED: {}", jobId, e.getMessage(), e);
            // Determine which status to set based on current
            Job job = jobRepository.findById(jobId).orElse(new Job());
            JobStatus errorStatus = switch (job.getStatus()) {
                case SCHEMA_GENERATING -> JobStatus.SCHEMA_GENERATE_FAILED;
                case SCHEMA_APPLYING -> JobStatus.SCHEMA_FAILED;
                case DATA_CONFIGURING -> JobStatus.DATA_CONFIG_FAILED;
                case DATA_RUNNING -> JobStatus.DATA_FAILED;
                case VALIDATING -> JobStatus.VALIDATION_FAILED;
                default -> JobStatus.DATA_FAILED; // Generic fallback
            };
            updateStatus(jobId, errorStatus, e.getMessage());
        }
    }

    // Helper to update state transactionally
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