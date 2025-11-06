package com.poc.migration.service.migration;

import com.poc.migration.exception.DataMigrationException;
import com.poc.migration.infrastructure.database.DatabaseConnectionConfig;
import com.poc.migration.infrastructure.database.DatabaseConnectionFactory;
import com.poc.migration.model.JobRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Service for registering and verifying storage units in ShardingSphere.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StorageUnitService {
    
    private final ShardingSphereConnectionService connectionService;
    private final DatabaseConnectionFactory connectionFactory;
    
    /**
     * Register source and target storage units in ShardingSphere Proxy.
     */
    public void registerSourceAndTarget(JobRequest request) {
        log.info("Registering source/target storage units in database: {}", 
                connectionService.getMigrationDatabaseName());
        
        try {
            // Get storage unit names (with defaults)
            String sourceUnitName = request.getSource().getStorageUnitNameOrDefault("source_ds");
            String targetUnitName = request.getTarget().getStorageUnitNameOrDefault("target_ds");
            
            log.info("Source storage unit name: {}", sourceUnitName);
            log.info("Target storage unit name: {}", targetUnitName);
            
            // Register source storage unit
            if (!sourceStorageUnitExists(request.getSource(), sourceUnitName)) {
                registerSourceStorageUnit(request.getSource(), sourceUnitName);
                log.info("✓ Source storage unit '{}' registered", sourceUnitName);
            } else {
                log.info("✓ Source storage unit '{}' already registered, skipping", sourceUnitName);
            }
            
            // Register target storage unit
            registerTargetStorageUnit(request.getTarget(), targetUnitName);
            log.info("✓ Target storage unit '{}' registered", targetUnitName);
            
            // Verify registration
            verifyStorageUnits(sourceUnitName, targetUnitName);
            
        } catch (Exception e) {
            throw new DataMigrationException("Failed to register storage units", e);
        }
    }
    
    /**
     * Check if source storage unit already exists.
     */
    private boolean sourceStorageUnitExists(JobRequest.DbConfig source, String storageUnitName) {
        try (Connection conn = connectionService.getMigrationConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SHOW MIGRATION SOURCE STORAGE UNITS")) {
            
            log.debug("Checking if source storage unit '{}' exists...", storageUnitName);
            
            while (rs.next()) {
                String name = rs.getString("name");
                String host = rs.getString("host");
                String db = rs.getString("db");
                
                if (storageUnitName.equals(name) && 
                    source.getHost().equals(host) && 
                    source.getDatabase().equals(db)) {
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.warn("Could not check source storage unit existence: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Register source storage unit.
     */
    private void registerSourceStorageUnit(JobRequest.DbConfig source, String storageUnitName) {
        String sourceUrl = buildStorageUnitUrl(source);
        
        String registerSourceSQL = String.format("""
            REGISTER MIGRATION SOURCE STORAGE UNIT %s (
                URL="%s",
                USER="%s",
                PASSWORD="%s",
                PROPERTIES("minPoolSize"="1","maxPoolSize"="20","idleTimeout"="60000")
            )
            """, storageUnitName, sourceUrl, source.getUser(), source.getPassword());
        
        executeDistSQL(registerSourceSQL);
    }
    
    /**
     * Register target storage unit.
     */
    private void registerTargetStorageUnit(JobRequest.DbConfig target, String storageUnitName) {
        String targetUrl = buildStorageUnitUrl(target);
        
        String registerTargetSQL = String.format("""
            REGISTER STORAGE UNIT IF NOT EXISTS %s (
                URL="%s",
                USER="%s",
                PASSWORD="%s",
                PROPERTIES("minPoolSize"="1","maxPoolSize"="20","idleTimeout"="60000")
            )
            """, storageUnitName, targetUrl, target.getUser(), target.getPassword());
        
        executeDistSQL(registerTargetSQL);
    }
    
    /**
     * Verify that storage units were registered correctly.
     */
    private void verifyStorageUnits(String sourceUnitName, String targetUnitName) {
        verifySourceStorageUnit(sourceUnitName);
        verifyTargetStorageUnit(targetUnitName);
    }
    
    /**
     * Verify source storage unit.
     */
    private void verifySourceStorageUnit(String storageUnitName) {
        try (Connection conn = connectionService.getMigrationConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SHOW MIGRATION SOURCE STORAGE UNITS")) {
            
            log.info("Verifying source storage unit '{}' registration...", storageUnitName);
            boolean hasSource = false;
            
            while (rs.next()) {
                String name = rs.getString("name");
                String type = rs.getString("type");
                String host = rs.getString("host");
                String port = rs.getString("port");
                String db = rs.getString("db");
                
                log.info("  Source unit: {} - {} - {}:{}/{}", name, type, host, port, db);
                
                if (storageUnitName.equals(name)) {
                    hasSource = true;
                }
            }
            
            if (!hasSource) {
                throw new DataMigrationException("Source storage unit '" + storageUnitName + "' not properly registered!");
            }
            
            log.info("✓ Source storage unit '{}' verified", storageUnitName);
            
        } catch (Exception e) {
            throw new DataMigrationException("Failed to verify source storage unit", e);
        }
    }
    
    /**
     * Verify target storage unit.
     */
    private void verifyTargetStorageUnit(String storageUnitName) {
        try (Connection conn = connectionService.getMigrationConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SHOW STORAGE UNITS")) {
            
            log.info("Verifying target storage unit '{}' registration...", storageUnitName);
            boolean hasTarget = false;
            
            while (rs.next()) {
                String name = rs.getString("name");
                String type = rs.getString("type");
                String host = rs.getString("host");
                String port = rs.getString("port");
                String db = rs.getString("db");
                
                log.info("  Target unit: {} - {} - {}:{}/{}", name, type, host, port, db);
                
                if (storageUnitName.equals(name)) {
                    hasTarget = true;
                }
            }
            
            if (!hasTarget) {
                throw new DataMigrationException("Target storage unit '" + storageUnitName + "' not properly registered!");
            }
            
            log.info("✓ Target storage unit '{}' verified", storageUnitName);
            
        } catch (Exception e) {
            throw new DataMigrationException("Failed to verify target storage unit", e);
        }
    }
    
    /**
     * Build storage unit URL using the connection factory.
     */
    private String buildStorageUnitUrl(JobRequest.DbConfig dbConfig) {
        DatabaseConnectionConfig config = convertToConnectionConfig(dbConfig);
        return connectionFactory.buildJdbcUrl(config);
    }
    
    /**
     * Convert JobRequest.DbConfig to DatabaseConnectionConfig.
     */
    private DatabaseConnectionConfig convertToConnectionConfig(JobRequest.DbConfig dbConfig) {
        return DatabaseConnectionConfig.builder()
            .type(com.poc.migration.infrastructure.database.DatabaseType.fromString(dbConfig.getType()))
            .host(dbConfig.getHost())
            .port(dbConfig.getPort())
            .database(dbConfig.getDatabase())
            .schema(dbConfig.getSchema())
            .user(dbConfig.getUser())
            .password(dbConfig.getPassword())
            .build();
    }
    
    /**
     * Execute a DistSQL command on ShardingSphere Proxy.
     */
    private void executeDistSQL(String sql) {
        String preview = sql.length() > 200 ? sql.substring(0, 200) + "..." : sql;
        log.debug("Executing DistSQL: {}", preview);
        
        try (Connection conn = connectionService.getMigrationConnection();
            Statement stmt = conn.createStatement()) {
            
            stmt.execute(sql);
            log.debug("✓ DistSQL executed successfully");
            
        } catch (Exception e) {
            log.error("Failed to execute DistSQL: {}", preview);
            throw new DataMigrationException("DistSQL execution failed", e);
        }
    }
}


