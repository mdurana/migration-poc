package com.poc.migration.orchestration.phases;

import com.poc.migration.config.MigrationProperties;
import com.poc.migration.executor.SchemaExecutor;
import com.poc.migration.orchestration.MigrationContext;
import com.poc.migration.orchestration.MigrationPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Phase for normalizing schema for target database.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SchemaNormalizationPhase implements MigrationPhase {
    
    private final SchemaExecutor schemaExecutor;
    private final MigrationProperties properties;
    
    @Override
    public void execute(MigrationContext context) throws Exception {
        log.info("[Job-{}] Normalizing schema for target database...", context.getJobId());
        
        String normalizedPath = properties.getSchema().getOutputDir() + 
            "job-" + context.getJobId() + "-changelog.normalized.xml";
        
        context.setNormalizedChangelogPath(normalizedPath);
        
        // Normalize schema
        schemaExecutor.normalizeChangelog(
            context.getRequest(),
            context.getGeneratedChangelogPath(),
            normalizedPath
        );
        
        log.info("[Job-{}] Schema normalization complete. Output: {}", 
                context.getJobId(), normalizedPath);
    }
    
    @Override
    public String getPhaseName() {
        return "Schema Normalization";
    }
    
    @Override
    public boolean shouldSkip(MigrationContext context) {
        // Skip for homogeneous MySQL migrations
        return context.isHomogeneousMySQL();
    }
}


