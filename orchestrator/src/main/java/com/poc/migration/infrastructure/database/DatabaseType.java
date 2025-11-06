package com.poc.migration.infrastructure.database;

import lombok.Getter;

/**
 * Enumeration of supported database types with their metadata.
 */
@Getter
public enum DatabaseType {
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver", 3306, "jdbc:mysql://"),
    POSTGRESQL("postgresql", "org.postgresql.Driver", 5432, "jdbc:postgresql://");

    private final String typeName;
    private final String driverClassName;
    private final int defaultPort;
    private final String jdbcPrefix;

    DatabaseType(String typeName, String driverClassName, int defaultPort, String jdbcPrefix) {
        this.typeName = typeName;
        this.driverClassName = driverClassName;
        this.defaultPort = defaultPort;
        this.jdbcPrefix = jdbcPrefix;
    }

    /**
     * Parse database type from string (case-insensitive).
     */
    public static DatabaseType fromString(String type) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("Database type cannot be null or empty");
        }
        
        for (DatabaseType dbType : values()) {
            if (dbType.typeName.equalsIgnoreCase(type)) {
                return dbType;
            }
        }
        
        throw new IllegalArgumentException("Unsupported database type: " + type);
    }

    /**
     * Check if this database type matches the given string.
     */
    public boolean matches(String type) {
        return this.typeName.equalsIgnoreCase(type);
    }
}


