package com.poc.migration.orchestration;

import com.poc.migration.model.JobRequest;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context object that carries state through the migration lifecycle.
 * Shared across all phases.
 */
@Data
public class MigrationContext {
    
    private final Long jobId;
    private final JobRequest request;
    
    // Paths to generated files
    private String generatedChangelogPath;
    private String normalizedChangelogPath;
    
    // Migration job IDs from ShardingSphere
    private List<String> migrationJobIds;
    
    // Additional metadata
    private final Map<String, Object> metadata = new HashMap<>();
    
    public MigrationContext(Long jobId, JobRequest request) {
        this.jobId = jobId;
        this.request = request;
    }
    
    /**
     * Check if migration is homogeneous (same source and target DB type).
     */
    public boolean isHomogeneousMigration() {
        return request.getSource().getType().equalsIgnoreCase(
            request.getTarget().getType()
        );
    }
    
    /**
     * Check if migration is homogeneous MySQL.
     */
    public boolean isHomogeneousMySQL() {
        return isHomogeneousMigration() && 
            request.getSource().getType().equalsIgnoreCase("mysql");
    }
    
    /**
     * Put arbitrary metadata.
     */
    public void putMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    /**
     * Get arbitrary metadata.
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        return (T) metadata.get(key);
    }
}


