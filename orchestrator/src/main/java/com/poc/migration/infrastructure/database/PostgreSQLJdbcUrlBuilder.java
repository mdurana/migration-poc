package com.poc.migration.infrastructure.database;

import org.springframework.stereotype.Component;

/**
 * PostgreSQL-specific JDBC URL builder.
 */
@Component
public class PostgreSQLJdbcUrlBuilder implements JdbcUrlBuilder {
    
    @Override
    public String buildUrl(DatabaseConnectionConfig config) {
        return String.format(
            "jdbc:postgresql://%s:%d/%s?ssl=false",
            config.getHost(),
            config.getPort(),
            config.getDatabase()
        );
    }
    
    @Override
    public boolean supports(DatabaseType type) {
        return type == DatabaseType.POSTGRESQL;
    }
}


