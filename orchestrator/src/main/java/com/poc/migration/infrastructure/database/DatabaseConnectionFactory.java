package com.poc.migration.infrastructure.database;

import com.poc.migration.exception.ConnectionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/**
 * Factory for creating database connections with proper URL building and driver loading.
 * Centralizes connection logic to eliminate duplication across executors.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseConnectionFactory {
    
    private final List<JdbcUrlBuilder> urlBuilders;
    
    /**
     * Create a database connection from configuration.
     */
    public Connection createConnection(DatabaseConnectionConfig config) {
        try {
            // Load driver class
            Class.forName(config.getType().getDriverClassName());
            
            // Build JDBC URL
            String jdbcUrl = buildJdbcUrl(config);
            
            log.debug("Creating connection to: {}", jdbcUrl);
            
            // Create connection
            return DriverManager.getConnection(
                jdbcUrl,
                config.getUser(),
                config.getPassword()
            );
            
        } catch (ClassNotFoundException e) {
            throw new ConnectionException(
                "Database driver not found: " + config.getType().getDriverClassName(),
                e
            );
        } catch (SQLException e) {
            throw new ConnectionException(
                "Failed to connect to database: " + config.getHost() + ":" + config.getPort(),
                e
            );
        }
    }
    
    /**
     * Build JDBC URL using appropriate strategy.
     */
    public String buildJdbcUrl(DatabaseConnectionConfig config) {
        return urlBuilders.stream()
            .filter(builder -> builder.supports(config.getType()))
            .findFirst()
            .map(builder -> builder.buildUrl(config))
            .orElseThrow(() -> new ConnectionException(
                "No JDBC URL builder found for database type: " + config.getType()
            ));
    }
    
    /**
     * Test connection validity.
     */
    public boolean testConnection(DatabaseConnectionConfig config) {
        try (Connection conn = createConnection(config)) {
            return conn.isValid(5); // 5 second timeout
        } catch (Exception e) {
            log.warn("Connection test failed: {}", e.getMessage());
            return false;
        }
    }
}


