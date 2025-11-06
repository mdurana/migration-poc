package com.poc.migration.exception;

/**
 * Exception thrown when database connection issues occur.
 */
public class ConnectionException extends MigrationException {
    
    public ConnectionException(String message) {
        super(message);
    }
    
    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}


