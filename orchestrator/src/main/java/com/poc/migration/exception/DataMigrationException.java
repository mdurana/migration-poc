package com.poc.migration.exception;

/**
 * Exception thrown when data migration operations fail.
 */
public class DataMigrationException extends MigrationException {
    
    public DataMigrationException(String message) {
        super(message);
    }
    
    public DataMigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}


