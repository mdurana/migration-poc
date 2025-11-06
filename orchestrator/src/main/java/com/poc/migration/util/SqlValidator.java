package com.poc.migration.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for SQL validation and SQL injection prevention.
 */
@Slf4j
public class SqlValidator {
    
    private SqlValidator() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Validates table name to prevent SQL injection.
     * Allows: letters, numbers, underscores, and hyphens.
     */
    public static boolean isValidTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return false;
        }
        
        // Allow alphanumeric, underscore, and hyphen
        // Disallow spaces, semicolons, quotes, etc.
        return tableName.matches("^[a-zA-Z0-9_-]+$");
    }
    
    /**
     * Validates database name to prevent SQL injection.
     */
    public static boolean isValidDatabaseName(String databaseName) {
        if (databaseName == null || databaseName.isEmpty()) {
            return false;
        }
        
        return databaseName.matches("^[a-zA-Z0-9_-]+$");
    }
    
    /**
     * Validates schema name to prevent SQL injection.
     */
    public static boolean isValidSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isEmpty()) {
            return false;
        }
        
        return schemaName.matches("^[a-zA-Z0-9_-]+$");
    }
    
    /**
     * Validates identifier (table, column, database, schema).
     */
    public static boolean isValidIdentifier(String identifier) {
        return isValidTableName(identifier); // Same rules
    }
    
    /**
     * Throws exception if table name is invalid.
     */
    public static void validateTableName(String tableName) {
        if (!isValidTableName(tableName)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }
    }
    
    /**
     * Throws exception if database name is invalid.
     */
    public static void validateDatabaseName(String databaseName) {
        if (!isValidDatabaseName(databaseName)) {
            throw new IllegalArgumentException("Invalid database name: " + databaseName);
        }
    }
}


