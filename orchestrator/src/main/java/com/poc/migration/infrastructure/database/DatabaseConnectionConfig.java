package com.poc.migration.infrastructure.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration holder for database connections.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatabaseConnectionConfig {
    private DatabaseType type;
    private String host;
    private int port;
    private String database;
    private String schema;
    private String user;
    private String password;
    
    /**
     * Get schema with default fallback.
     */
    public String getSchemaOrDefault() {
        if (schema != null && !schema.isEmpty()) {
            return schema;
        }
        return type == DatabaseType.POSTGRESQL ? "public" : database;
    }
}


