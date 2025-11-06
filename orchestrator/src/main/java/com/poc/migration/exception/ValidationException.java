package com.poc.migration.exception;

/**
 * Exception thrown when validation checks fail.
 */
public class ValidationException extends MigrationException {
    
    public ValidationException(String message) {
        super(message);
    }
    
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}


