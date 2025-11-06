package com.poc.migration.exception;

/**
 * Exception thrown when configuration errors occur.
 */
public class ConfigurationException extends MigrationException {
    
    public ConfigurationException(String message) {
        super(message);
    }
    
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}


