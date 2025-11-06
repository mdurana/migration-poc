package com.poc.migration.infrastructure.database;

import org.springframework.stereotype.Component;

/**
 * MySQL-specific JDBC URL builder.
 */
@Component
public class MySQLJdbcUrlBuilder implements JdbcUrlBuilder {
    
    @Override
    public String buildUrl(DatabaseConnectionConfig config) {
        return String.format(
            "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            config.getHost(),
            config.getPort(),
            config.getDatabase()
        );
    }
    
    @Override
    public boolean supports(DatabaseType type) {
        return type == DatabaseType.MYSQL;
    }
}


