package com.poc.migration.orchestration;

/**
 * Interface for migration phases.
 * Each phase represents a distinct step in the migration lifecycle.
 */
public interface MigrationPhase {
    
    /**
     * Execute this phase of the migration.
     * 
     * @param context The migration context containing job information
     * @throws Exception if the phase fails
     */
    void execute(MigrationContext context) throws Exception;
    
    /**
     * Get the name of this phase for logging.
     */
    String getPhaseName();
    
    /**
     * Check if this phase should be skipped based on context.
     * Default implementation never skips.
     */
    default boolean shouldSkip(MigrationContext context) {
        return false;
    }
}

