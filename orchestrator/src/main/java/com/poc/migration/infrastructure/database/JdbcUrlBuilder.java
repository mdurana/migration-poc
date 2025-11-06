package com.poc.migration.infrastructure.database;

/**
 * Strategy interface for building database-specific JDBC URLs.
 */
public interface JdbcUrlBuilder {
    
    /**
     * Build JDBC URL for the given configuration.
     */
    String buildUrl(DatabaseConnectionConfig config);
    
    /**
     * Check if this builder supports the given database type.
     */
    boolean supports(DatabaseType type);
}


