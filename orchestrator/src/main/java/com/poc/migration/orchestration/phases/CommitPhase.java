package com.poc.migration.orchestration.phases;

import com.poc.migration.orchestration.MigrationContext;
import com.poc.migration.orchestration.MigrationPhase;
import com.poc.migration.service.migration.MigrationCommitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Phase for committing migration (cutover).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommitPhase implements MigrationPhase {
    
    private final MigrationCommitService commitService;
    
    @Override
    public void execute(MigrationContext context) throws Exception {
        log.info("[Job-{}] Committing migration (cutover)...", context.getJobId());
        
        // Commit all migration jobs
        commitService.commitMigrations(context.getMigrationJobIds());
        
        log.info("[Job-{}] Migration committed successfully. Target is now active", 
                context.getJobId());
    }
    
    @Override
    public String getPhaseName() {
        return "Commit";
    }
}


