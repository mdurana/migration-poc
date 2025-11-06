package com.poc.migration.orchestration;

import com.poc.migration.exception.MigrationException;
import com.poc.migration.model.JobRequest;
import com.poc.migration.model.JobStatus;
import com.poc.migration.orchestration.phases.*;
import com.poc.migration.service.migration.MigrationCommitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrator for the migration lifecycle.
 * Coordinates execution of migration phases in sequence.
 * Replaces the large runMigrationLifecycle method from JobService.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MigrationOrchestrator {
    
    // Phase implementations
    private final SchemaGenerationPhase schemaGenerationPhase;
    private final SchemaNormalizationPhase schemaNormalizationPhase;
    private final SchemaApplicationPhase schemaApplicationPhase;
    private final DataConfigurationPhase dataConfigurationPhase;
    private final DataMigrationPhase dataMigrationPhase;
    private final ValidationPhase validationPhase;
    private final CommitPhase commitPhase;
    
    // For rollback
    private final MigrationCommitService commitService;
    
    /**
     * Execute the complete migration lifecycle.
     * 
     * @param jobId The job ID
     * @param request The migration request
     * @param statusCallback Callback to update job status
     * @return The final job status
     */
    public JobStatus executeMigrationLifecycle(
            Long jobId, 
            JobRequest request,
            StatusUpdateCallback statusCallback) {
        
        log.info("[Job-{}] ========== MIGRATION LIFECYCLE STARTED ==========", jobId);
        
        // Create context
        MigrationContext context = new MigrationContext(jobId, request);
        
        // Log migration type
        if (context.isHomogeneousMySQL()) {
            log.info("[Job-{}] Homogeneous MySQL migration detected. Skipping schema generation.", jobId);
        } else {
            log.info("[Job-{}] Migration detected ({} -> {}). Full schema workflow enabled.", 
                    jobId, request.getSource().getType(), request.getTarget().getType());
        }
        
        try {
            // Execute phases in order
            executePhaseIfNeeded(schemaGenerationPhase, context, JobStatus.SCHEMA_GENERATING, statusCallback);
            executePhaseIfNeeded(schemaNormalizationPhase, context, JobStatus.SCHEMA_NORMALIZING, statusCallback);
            executePhaseIfNeeded(schemaApplicationPhase, context, JobStatus.SCHEMA_APPLYING, statusCallback);
            executePhaseIfNeeded(dataConfigurationPhase, context, JobStatus.DATA_CONFIGURING, statusCallback);
            executePhaseIfNeeded(dataMigrationPhase, context, JobStatus.DATA_RUNNING, statusCallback);
            executePhaseIfNeeded(validationPhase, context, JobStatus.VALIDATING, statusCallback);
            executePhaseIfNeeded(commitPhase, context, JobStatus.COMMITTING, statusCallback);
            
            log.info("[Job-{}] ========== MIGRATION LIFECYCLE COMPLETE ==========", jobId);
            return JobStatus.DONE;
            
        } catch (Exception e) {
            log.error("[Job-{}] ========== MIGRATION LIFECYCLE FAILED ==========", jobId);
            log.error("[Job-{}] Error: {}", jobId, e.getMessage(), e);
            
            // Attempt rollback
            rollbackIfNeeded(context);
            
            // Determine appropriate error status
            return determineErrorStatus(context, e);
        }
    }
    
    /**
     * Execute a phase if it shouldn't be skipped.
     */
    private void executePhaseIfNeeded(
            MigrationPhase phase,
            MigrationContext context,
            JobStatus status,
            StatusUpdateCallback statusCallback) throws Exception {
        
        if (phase.shouldSkip(context)) {
            log.info("[Job-{}] Skipping phase: {}", context.getJobId(), phase.getPhaseName());
            return;
        }
        
        log.info("[Job-{}] Starting phase: {}", context.getJobId(), phase.getPhaseName());
        statusCallback.updateStatus(status, null);
        
        try {
            phase.execute(context);
            log.info("[Job-{}] Completed phase: {}", context.getJobId(), phase.getPhaseName());
            
        } catch (Exception e) {
            log.error("[Job-{}] Failed phase: {}", context.getJobId(), phase.getPhaseName());
            throw new MigrationException(
                "Phase '" + phase.getPhaseName() + "' failed: " + e.getMessage(), 
                e
            );
        }
    }
    
    /**
     * Rollback migration jobs if they were created.
     */
    private void rollbackIfNeeded(MigrationContext context) {
        List<String> jobIds = context.getMigrationJobIds();
        
        if (jobIds != null && !jobIds.isEmpty()) {
            try {
                log.warn("[Job-{}] Attempting to rollback {} migration jobs...", 
                        context.getJobId(), jobIds.size());
                commitService.rollbackMigrations(jobIds);
                
            } catch (Exception rollbackEx) {
                log.error("[Job-{}] Rollback failed: {}", 
                        context.getJobId(), rollbackEx.getMessage());
            }
        }
    }
    
    /**
     * Determine appropriate error status based on the current context.
     */
    private JobStatus determineErrorStatus(MigrationContext context, Exception error) {
        // Check what data we have to determine where we failed
        if (context.getMigrationJobIds() != null && !context.getMigrationJobIds().isEmpty()) {
            // We got to data migration phase
            if (error.getMessage() != null && error.getMessage().contains("Validation")) {
                return JobStatus.VALIDATION_FAILED;
            } else if (error.getMessage() != null && error.getMessage().contains("Commit")) {
                return JobStatus.COMMIT_FAILED;
            }
            return JobStatus.DATA_FAILED;
        }
        
        if (context.getNormalizedChangelogPath() != null) {
            return JobStatus.SCHEMA_FAILED;
        }
        
        if (context.getGeneratedChangelogPath() != null) {
            return JobStatus.SCHEMA_NORMALIZE_FAILED;
        }
        
        // Default to schema generation failure
        return JobStatus.SCHEMA_GENERATE_FAILED;
    }
    
    /**
     * Callback interface for status updates.
     */
    @FunctionalInterface
    public interface StatusUpdateCallback {
        void updateStatus(JobStatus status, String error);
    }
}


