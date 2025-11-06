package com.poc.migration.orchestration.phases;

import com.poc.migration.orchestration.MigrationContext;
import com.poc.migration.orchestration.MigrationPhase;
import com.poc.migration.service.migration.MigrationJobService;
import com.poc.migration.service.migration.StorageUnitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase for configuring data migration in ShardingSphere.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DataConfigurationPhase implements MigrationPhase {
    
    private final StorageUnitService storageUnitService;
    private final MigrationJobService migrationJobService;
    
    @Override
    public void execute(MigrationContext context) throws Exception {
        log.info("[Job-{}] Registering storage units in ShardingSphere Proxy...", 
                context.getJobId());
        
        // Register source and target
        storageUnitService.registerSourceAndTarget(context.getRequest());
        
        log.info("[Job-{}] Creating migration jobs for {} tables...", 
                context.getJobId(), context.getRequest().getTablesToMigrate().size());
        
        // Create migration jobs
        List<String> jobIds = migrationJobService.createMigrationJobs(context.getRequest());
        context.setMigrationJobIds(jobIds);
        
        log.info("[Job-{}] Created {} migration jobs: {}", 
                context.getJobId(), jobIds.size(), jobIds);
    }
    
    @Override
    public String getPhaseName() {
        return "Data Configuration";
    }
}


