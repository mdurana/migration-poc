package com.poc.migration.model;

/**
 * Represents the current status of a migration job.
 */
public enum JobStatus {
    // Initial state
    PENDING("Pending", false, false),
    
    // Schema generation phase
    SCHEMA_GENERATING("Generating Schema", false, false),
    SCHEMA_GENERATE_FAILED("Schema Generation Failed", true, true),
    
    // Schema normalization phase (heterogeneous migrations)
    SCHEMA_NORMALIZING("Normalizing Schema", false, false),
    SCHEMA_NORMALIZE_FAILED("Schema Normalization Failed", true, true),
    
    // Schema application phase
    SCHEMA_APPLYING("Applying Schema", false, false),
    SCHEMA_FAILED("Schema Application Failed", true, true),
    
    // Data configuration phase
    DATA_CONFIGURING("Configuring Data Migration", false, false),
    DATA_CONFIG_FAILED("Data Configuration Failed", true, true),
    
    // Data migration phase
    DATA_RUNNING("Migrating Data", false, false),
    DATA_FAILED("Data Migration Failed", true, true),
    
    // Validation phase
    VALIDATING("Validating Migration", false, false),
    VALIDATION_FAILED("Validation Failed", true, true),
    
    // Commit phase
    COMMITTING("Committing Migration", false, false),
    COMMIT_FAILED("Commit Failed", true, true),
    
    // Terminal success state
    DONE("Completed Successfully", true, false);

    private final String displayName;
    private final boolean isTerminal;
    private final boolean isError;

    JobStatus(String displayName, boolean isTerminal, boolean isError) {
        this.displayName = displayName;
        this.isTerminal = isTerminal;
        this.isError = isError;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return isTerminal;
    }

    public boolean isError() {
        return isError;
    }

    public boolean isRunning() {
        return !isTerminal;
    }

    public boolean isSuccess() {
        return this == DONE;
    }
}