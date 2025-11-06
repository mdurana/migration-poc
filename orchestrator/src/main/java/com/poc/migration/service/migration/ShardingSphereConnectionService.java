package com.poc.migration.service.migration;

import com.poc.migration.config.ShardingSphereProperties;
import com.poc.migration.exception.ConnectionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Service for managing connections to ShardingSphere Proxy.
 * Centralizes proxy connection logic and protocol handling.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ShardingSphereConnectionService {
    
    private final ShardingSphereProperties properties;
    
    /**
     * Create connection to a specific database in ShardingSphere Proxy.
     */
    public Connection getConnection(String database) {
        try {
            String jdbcUrl = buildProxyUrl(database);
            log.debug("Connecting to ShardingSphere Proxy: {}", jdbcUrl);
            
            return DriverManager.getConnection(
                jdbcUrl,
                properties.getUser(),
                properties.getPassword()
            );
            
        } catch (SQLException e) {
            throw new ConnectionException(
                "Failed to connect to ShardingSphere Proxy database: " + database,
                e
            );
        }
    }
    
    /**
     * Create connection to the admin database.
     */
    public Connection getAdminConnection() {
        return getConnection(properties.getDatabase());
    }
    
    /**
     * Create connection to the migration database.
     */
    public Connection getMigrationConnection() {
        return getConnection(properties.getMigrationDb());
    }
    
    /**
     * Build JDBC URL for ShardingSphere Proxy.
     * Uses MySQL protocol by default.
     */
    private String buildProxyUrl(String database) {
        // Determine protocol based on configured type
        if (isPostgreSQL()) {
            return String.format(
                "jdbc:postgresql://%s:%d/%s?useSSL=false",
                properties.getHost(),
                properties.getPort(),
                database
            );
        }
        
        // Default to MySQL protocol
        return String.format(
            "jdbc:mysql://%s:%d/%s",
            properties.getHost(),
            properties.getPort(),
            database
        );
    }
    
    /**
     * Check if the proxy is using PostgreSQL protocol.
     */
    public boolean isPostgreSQL() {
        return "postgresql".equalsIgnoreCase(properties.getType()) 
            || "postgres".equalsIgnoreCase(properties.getType());
    }
    
    /**
     * Check if the proxy is using MySQL protocol.
     */
    public boolean isMySQL() {
        return "mysql".equalsIgnoreCase(properties.getType());
    }
    
    /**
     * Test connection to ShardingSphere Proxy.
     */
    public boolean testConnection() {
        try (Connection conn = getAdminConnection()) {
            return conn.isValid(5);
        } catch (Exception e) {
            log.warn("ShardingSphere Proxy connection test failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get migration database name.
     */
    public String getMigrationDatabaseName() {
        return properties.getMigrationDb();
    }
}

