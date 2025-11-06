package com.poc.migration.orchestration.phases;

import com.poc.migration.exception.ValidationException;
import com.poc.migration.executor.ValidationExecutor;
import com.poc.migration.orchestration.MigrationContext;
import com.poc.migration.orchestration.MigrationPhase;
import com.poc.migration.service.migration.MigrationJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Phase for validating migration results.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ValidationPhase implements MigrationPhase {
    
    private final ValidationExecutor validationExecutor;
    private final MigrationJobService migrationJobService;
    
    @Override
    public void execute(MigrationContext context) throws Exception {
        log.info("[Job-{}] Running validation checks...", context.getJobId());
        
        // Show migration status for each job
        for (String jobId : context.getMigrationJobIds()) {
            log.info("[Job-{}] Checking status for migration job: {}", 
                    context.getJobId(), jobId);
            migrationJobService.showJobStatus(jobId);
        }
        
        // Validate row counts
        boolean isValid = validationExecutor.validateRowCounts(context.getRequest());
        
        if (!isValid) {
            throw new ValidationException("Row counts do not match between source and target");
        }
        
        log.info("[Job-{}] All validation checks passed", context.getJobId());
    }
    
    @Override
    public String getPhaseName() {
        return "Validation";
    }
}


