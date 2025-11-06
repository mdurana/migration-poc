package com.poc.migration.orchestration.phases;

import com.poc.migration.executor.SchemaExecutor;
import com.poc.migration.orchestration.MigrationContext;
import com.poc.migration.orchestration.MigrationPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Phase for applying schema to target database.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SchemaApplicationPhase implements MigrationPhase {
    
    private final SchemaExecutor schemaExecutor;
    
    @Override
    public void execute(MigrationContext context) throws Exception {
        log.info("[Job-{}] Applying schema to target database...", context.getJobId());
        
        // Apply normalized schema
        schemaExecutor.applyChangelog(
            context.getRequest(),
            context.getNormalizedChangelogPath()
        );
        
        log.info("[Job-{}] Schema applied successfully to target", context.getJobId());
    }
    
    @Override
    public String getPhaseName() {
        return "Schema Application";
    }
    
    @Override
    public boolean shouldSkip(MigrationContext context) {
        // Skip for homogeneous MySQL migrations
        return context.isHomogeneousMySQL();
    }
}


