package com.poc.migration.exception;

/**
 * Base exception for all migration-related errors.
 */
public class MigrationException extends RuntimeException {
    
    public MigrationException(String message) {
        super(message);
    }
    
    public MigrationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public MigrationException(Throwable cause) {
        super(cause);
    }
}


