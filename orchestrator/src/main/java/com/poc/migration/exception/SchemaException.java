package com.poc.migration.exception;

/**
 * Exception thrown when schema generation, normalization, or application fails.
 */
public class SchemaException extends MigrationException {
    
    public SchemaException(String message) {
        super(message);
    }
    
    public SchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}


