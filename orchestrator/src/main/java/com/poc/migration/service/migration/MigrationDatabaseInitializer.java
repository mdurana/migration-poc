package com.poc.migration.service.migration;

import com.poc.migration.config.MigrationProperties;
import com.poc.migration.exception.DataMigrationException;
import com.poc.migration.util.RetryUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Service for initializing the migration database in ShardingSphere Proxy.
 * Runs once on application startup.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MigrationDatabaseInitializer {
    
    private final ShardingSphereConnectionService connectionService;
    private final MigrationProperties properties;
    
    /**
     * Ensures the logical migration database exists on application startup.
     */
    @PostConstruct
    public void ensureMigrationDatabase() {
        String migrationDb = connectionService.getMigrationDatabaseName();
        
        try {
            log.info("Ensuring logical migration database exists: {}", migrationDb);
            
            // Check if database already exists
            if (databaseExists(migrationDb)) {
                log.info("✓ Migration database '{}' already exists", migrationDb);
            } else {
                // Create the database
                createDatabase(migrationDb);
                log.info("✓ Migration database '{}' created", migrationDb);
                
                // Wait for database to be ready
                Thread.sleep(1000);
            }
            
            // Verify we can connect to the migration database
            verifyDatabaseConnection(migrationDb);
            log.info("✓ Migration database setup complete");
            
        } catch (Exception e) {
            log.error("Failed to ensure migration database: {}", e.getMessage(), e);
            throw new DataMigrationException("Migration database setup failed", e);
        }
    }
    
    /**
     * Check if the migration database already exists.
     */
    private boolean databaseExists(String databaseName) {
        String query = getListDatabasesQuery();
        
        try (Connection conn = connectionService.getAdminConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                String dbName = rs.getString(1);
                if (databaseName.equals(dbName)) {
                    return true;
                }
            }
            return false;
            
        } catch (Exception e) {
            log.debug("Could not check if database exists: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Create the migration database.
     */
    private void createDatabase(String databaseName) {
        log.info("Creating migration database: {}", databaseName);
        
        try (Connection conn = connectionService.getAdminConnection();
            Statement stmt = conn.createStatement()) {
            
            String createDbSQL = getCreateDatabaseQuery(databaseName);
            stmt.execute(createDbSQL);
            log.info("✓ Database creation command executed");
            
        } catch (Exception e) {
            throw new DataMigrationException("Failed to create database: " + databaseName, e);
        }
    }
    
    /**
     * Get database-specific query to list databases.
     */
    private String getListDatabasesQuery() {
        if (connectionService.isPostgreSQL()) {
            // PostgreSQL: Query system catalog
            return "SELECT datname FROM pg_database WHERE datistemplate = false";
        }
        // MySQL: Use SHOW DATABASES
        return "SHOW DATABASES";
    }
    
    /**
     * Get database-specific query to create database.
     */
    private String getCreateDatabaseQuery(String databaseName) {
        if (connectionService.isPostgreSQL()) {
            // PostgreSQL: CREATE DATABASE (no IF NOT EXISTS support in older versions)
            return String.format("CREATE DATABASE %s", databaseName);
        }
        // MySQL: CREATE DATABASE
        return String.format("CREATE DATABASE IF NOT EXISTS %s", databaseName);
    }
    
    /**
     * Verify connection to the migration database with retry logic.
     */
    private void verifyDatabaseConnection(String databaseName) throws Exception {
        int maxRetries = properties.getRetry().getMaxAttempts();
        long retryDelay = properties.getRetry().getDelayMs();
        
        RetryUtil.executeWithRetry(
            () -> {
                try (Connection conn = connectionService.getConnection(databaseName)) {
                    if (!conn.isValid(5)) {
                        throw new DataMigrationException("Connection validation failed");
                    }
                    log.info("✓ Successfully connected to migration database: {}", databaseName);
                    return true;
                } catch (Exception e) {
                    throw new DataMigrationException("Connection failed: " + e.getMessage(), e);
                }
            },
            maxRetries,
            retryDelay,
            "migration database connection"
        );
    }
}


