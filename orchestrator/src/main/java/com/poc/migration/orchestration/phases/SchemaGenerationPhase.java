package com.poc.migration.orchestration.phases;

import com.poc.migration.config.MigrationProperties;
import com.poc.migration.executor.SchemaExecutor;
import com.poc.migration.orchestration.MigrationContext;
import com.poc.migration.orchestration.MigrationPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Phase for generating schema from source database.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SchemaGenerationPhase implements MigrationPhase {
    
    private final SchemaExecutor schemaExecutor;
    private final MigrationProperties properties;
    
    @Override
    public void execute(MigrationContext context) throws Exception {
        log.info("[Job-{}] Generating schema from source...", context.getJobId());
        
        // Build paths
        String generatedPath = properties.getSchema().getOutputDir() + 
            "job-" + context.getJobId() + "-changelog.xml";
        
        context.setGeneratedChangelogPath(generatedPath);
        
        // Generate schema
        schemaExecutor.generateChangelog(context.getRequest(), generatedPath);
        
        log.info("[Job-{}] Schema generated successfully to {}", 
                context.getJobId(), generatedPath);
    }
    
    @Override
    public String getPhaseName() {
        return "Schema Generation";
    }
    
    @Override
    public boolean shouldSkip(MigrationContext context) {
        // Skip for homogeneous MySQL migrations
        return context.isHomogeneousMySQL();
    }
}


