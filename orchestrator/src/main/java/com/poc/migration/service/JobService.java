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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository jobRepository;
    private final SchemaExecutor schemaExecutor;
    private final DataExecutor dataExecutor;
    private final ValidationExecutor validationExecutor;
    private final ObjectMapper objectMapper;

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

    @Async
    public void runMigrationLifecycle(Long jobId, JobRequest request) {
        log.info("[Job-{}] Lifecycle started.", jobId);
        
        String generatedChangelogPath = schemaOutputDir + "job-" + jobId + "-changelog.xml";
        String normalizedChangelogPath = schemaOutputDir + "job-" + jobId + "-changelog.normalized.xml";

        // Track migration job IDs for later operations
        List<String> migrationJobIds = null;

        try {
            // --- HETEROGENEOUS CHECK ---
            boolean isHomogeneous = Objects.equals(
                    request.getSource().getType(),
                    request.getTarget().getType()
            );

            if (isHomogeneous) {
                log.info("[Job-{}] Homogeneous migration detected ({}). Skipping schema generation and normalization.", 
                        jobId, request.getSource().getType());
            } else {
                log.info("[Job-{}] Heterogeneous migration detected ({} -> {}). Starting automated schema generation and normalization.", 
                jobId, request.getSource().getType(), request.getTarget().getType());
                
                // === 1. SCHEMA GENERATION ===
                updateStatus(jobId, JobStatus.SCHEMA_GENERATING, null);
                log.info("[Job-{}] Generating schema from source...", jobId);
                schemaExecutor.generateChangelog(request, generatedChangelogPath);
                log.info("[Job-{}] Schema generated successfully to {}.", jobId, generatedChangelogPath);
                
                // === 2. SCHEMA NORMALIZATION ===
                updateStatus(jobId, JobStatus.SCHEMA_NORMALIZING, null);
                schemaExecutor.normalizeChangelog(request, generatedChangelogPath, normalizedChangelogPath);
                log.info("[Job-{}] Schema normalization complete. Output: {}", jobId, normalizedChangelogPath);

                // === 3. SCHEMA APPLY ===
                updateStatus(jobId, JobStatus.SCHEMA_APPLYING, null);
                log.info("[Job-{}] Applying schema to target database...", jobId);
                schemaExecutor.applyChangelog(request, normalizedChangelogPath);
                log.info("[Job-{}] Schema applied successfully to target.", jobId);
            }

            // === 4. DATA CONFIGURATION ===
            updateStatus(jobId, JobStatus.DATA_CONFIGURING, null);
            log.info("[Job-{}] Registering storage units in ShardingSphere Proxy...", jobId);
            dataExecutor.registerSourceAndTarget(request);
            
            log.info("[Job-{}] Creating migration jobs for {} tables...", 
                    jobId, request.getTablesToMigrate().size());
            migrationJobIds = dataExecutor.createMigrationJobs(request);
            log.info("[Job-{}] Created {} migration jobs: {}", jobId, migrationJobIds.size(), migrationJobIds);

            // === 5. DATA EXECUTION (Inventory + Incremental Sync) ===
            updateStatus(jobId, JobStatus.DATA_RUNNING, null);
            log.info("[Job-{}] Starting data migration (inventory + CDC)...", jobId);
            
            // Jobs auto-start in ShardingSphere 5.5.2, just verify
            dataExecutor.startMigrationJobs();
            
            // Monitor until all jobs reach incremental sync
            log.info("[Job-{}] Monitoring migration progress...", jobId);
            dataExecutor.monitorJobsUntilReady();
            log.info("[Job-{}] All migrations are in incremental sync and ready for cutover.", jobId);

            // === 6. VALIDATION ===
            updateStatus(jobId, JobStatus.VALIDATING, null);
            log.info("[Job-{}] Running validation checks...", jobId);
            
            // Run consistency checks for each migration job
            for (String migrationJobId : migrationJobIds) {
                log.info("[Job-{}] Checking status for migration job: {}", jobId, migrationJobId);
                dataExecutor.showMigrationStatus(migrationJobId);
            }
            
            // Validate row counts
            boolean isValid = validationExecutor.validateRowCounts(request);
            if (!isValid) {
                throw new RuntimeException("Validation Failed: Row counts do not match.");
            }
            log.info("[Job-{}] All validation checks passed.", jobId);

            // === 7. CUTOVER (COMMIT) ===
            updateStatus(jobId, JobStatus.COMMITTING, null);
            log.info("[Job-{}] Committing migration (cutover)...", jobId);
            
            for (String migrationJobId : migrationJobIds) {
                log.info("[Job-{}] Committing migration job: {}", jobId, migrationJobId);
                dataExecutor.commitMigration(migrationJobId);
            }
            
            log.info("[Job-{}] Migration committed successfully. Target is now active.", jobId);

            // === 8. DONE ===
            updateStatus(jobId, JobStatus.DONE, null);
            log.info("[Job-{}] ========== MIGRATION LIFECYCLE COMPLETE ==========", jobId);

        } catch (Exception e) {
            log.error("[Job-{}] ========== MIGRATION LIFECYCLE FAILED ==========", jobId);
            log.error("[Job-{}] Error: {}", jobId, e.getMessage(), e);
            
            // Attempt rollback if we got far enough to create migration jobs
            if (migrationJobIds != null && !migrationJobIds.isEmpty()) {
                try {
                    log.warn("[Job-{}] Attempting to rollback {} migration jobs...", 
                            jobId, migrationJobIds.size());
                    for (String migrationJobId : migrationJobIds) {
                        try {
                            dataExecutor.rollbackMigration(migrationJobId);
                            log.info("[Job-{}] Rolled back migration job: {}", jobId, migrationJobId);
                        } catch (Exception rollbackEx) {
                            log.error("[Job-{}] Failed to rollback job {}: {}", 
                                    jobId, migrationJobId, rollbackEx.getMessage());
                        }
                    }
                } catch (Exception rollbackEx) {
                    log.error("[Job-{}] Rollback failed: {}", jobId, rollbackEx.getMessage());
                }
            }
            
            // Determine appropriate error status
            Job job = jobRepository.findById(jobId).orElse(new Job());
            JobStatus errorStatus = switch (job.getStatus()) {
                case SCHEMA_GENERATING -> JobStatus.SCHEMA_GENERATE_FAILED;
                case SCHEMA_NORMALIZING -> JobStatus.SCHEMA_NORMALIZE_FAILED;
                case SCHEMA_APPLYING -> JobStatus.SCHEMA_FAILED;
                case DATA_CONFIGURING -> JobStatus.DATA_CONFIG_FAILED;
                case DATA_RUNNING -> JobStatus.DATA_FAILED;
                case VALIDATING -> JobStatus.VALIDATION_FAILED;
                case COMMITTING -> JobStatus.COMMIT_FAILED;
                default -> JobStatus.DATA_FAILED;
            };
            
            updateStatus(jobId, errorStatus, e.getMessage());
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