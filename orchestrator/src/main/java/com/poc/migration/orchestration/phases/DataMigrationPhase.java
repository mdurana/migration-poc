package com.poc.migration.orchestration.phases;

import com.poc.migration.orchestration.MigrationContext;
import com.poc.migration.orchestration.MigrationPhase;
import com.poc.migration.service.migration.MigrationJobService;
import com.poc.migration.service.migration.MigrationMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Phase for executing data migration (inventory + incremental sync).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DataMigrationPhase implements MigrationPhase {
    
    private final MigrationJobService migrationJobService;
    private final MigrationMonitorService monitorService;
    
    @Override
    public void execute(MigrationContext context) throws Exception {
        log.info("[Job-{}] Starting data migration (inventory + CDC)...", context.getJobId());
        
        // Jobs auto-start in ShardingSphere 5.5.2, just verify
        migrationJobService.verifyJobsStarted();
        
        // Monitor until all jobs reach incremental sync
        log.info("[Job-{}] Monitoring migration progress...", context.getJobId());
        monitorService.monitorJobsUntilReady(context.getMigrationJobIds());
        
        log.info("[Job-{}] All migrations are in incremental sync and ready for cutover", 
                context.getJobId());
    }
    
    @Override
    public String getPhaseName() {
        return "Data Migration";
    }
}


