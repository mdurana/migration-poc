package com.poc.migration.service.migration;

import com.poc.migration.exception.DataMigrationException;
import com.poc.migration.model.JobRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for creating and managing migration jobs in ShardingSphere.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MigrationJobService {
    
    private final ShardingSphereConnectionService connectionService;
    
    /**
     * Create migration jobs for each table.
     * Returns list of actual job IDs from ShardingSphere.
     */
    public List<String> createMigrationJobs(JobRequest request) {
        List<String> tables = request.getTablesToMigrate();
        
        // Get storage unit names
        String sourceUnitName = request.getSource().getStorageUnitNameOrDefault("source_ds");
        String targetUnitName = request.getTarget().getStorageUnitNameOrDefault("target_ds");
        
        log.info("Creating migration jobs for {} tables (source: {}, target: {})", 
                tables.size(), sourceUnitName, targetUnitName);
        
        try {
            // Create migration job for each table
            for (String tableName : tables) {
                createMigrationJob(sourceUnitName, targetUnitName, tableName);
            }
            
            log.info("✓ Created {} migration jobs", tables.size());
            
            // Wait for jobs to register
            Thread.sleep(2000);
            
            // Return actual job IDs from ShardingSphere
            return getActualJobIds();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataMigrationException("Migration job creation interrupted", e);
        } catch (Exception e) {
            throw new DataMigrationException("Failed to create migration jobs", e);
        }
    }
    
    /**
     * Create a migration job for a single table.
     */
    private void createMigrationJob(String sourceUnitName, String targetUnitName, String tableName) {
        String migrateSQL = String.format(
            "MIGRATE TABLE %s.%s INTO %s",
            sourceUnitName, tableName, tableName
        );
        
        log.info("Creating migration for table: {} (from {} to {})", tableName, sourceUnitName, targetUnitName);
        log.debug("Migration command: {}", migrateSQL);
        
        executeDistSQL(migrateSQL);
    }
    
    /**
     * Retrieve actual job IDs from ShardingSphere after job creation.
     */
    public List<String> getActualJobIds() {
        List<String> actualJobIds = new ArrayList<>();
        
        try (Connection conn = connectionService.getMigrationConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SHOW MIGRATION LIST")) {
            
            while (rs.next()) {
                String jobId = rs.getString("id");
                actualJobIds.add(jobId);
                
                String tables = rs.getString("tables");
                String active = rs.getString("active");
                log.info("  Registered job ID: {} for tables: {} (active: {})", 
                        jobId, tables, active);
            }
            
        } catch (Exception e) {
            throw new DataMigrationException("Failed to retrieve job IDs", e);
        }
        
        log.info("✓ Retrieved {} actual job IDs from ShardingSphere", actualJobIds.size());
        return actualJobIds;
    }
    
    /**
     * Check if migration jobs are running.
     */
    public void verifyJobsStarted() {
        try (Connection conn = connectionService.getMigrationConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SHOW MIGRATION LIST")) {
            
            log.info("Checking migration job status...");
            boolean hasJobs = false;
            
            while (rs.next()) {
                hasJobs = true;
                String id = rs.getString("id");
                String tables = rs.getString("tables");
                String active = rs.getString("active");
                log.info("  Job ID: {}, Tables: {}, Active: {}", id, tables, active);
            }
            
            if (!hasJobs) {
                log.warn("No migration jobs found! Jobs may not have been created successfully.");
            }
            
        } catch (Exception e) {
            throw new DataMigrationException("Failed to verify job status", e);
        }
    }
    
    /**
     * Get detailed status of a specific migration job.
     */
    public void showJobStatus(String jobId) {
        String statusSQL = String.format("SHOW MIGRATION STATUS '%s'", jobId);
        
        try (Connection conn = connectionService.getMigrationConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(statusSQL)) {
            
            log.info("Migration status for job {}:", jobId);
            
            while (rs.next()) {
                int columnCount = rs.getMetaData().getColumnCount();
                StringBuilder sb = new StringBuilder();
                
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = rs.getMetaData().getColumnName(i);
                    String value = rs.getString(i);
                    sb.append(columnName).append("=").append(value).append(", ");
                }
                
                log.info("  {}", sb.toString());
            }
            
        } catch (Exception e) {
            log.warn("Could not retrieve detailed status for job {}: {}", jobId, e.getMessage());
        }
    }
    
    /**
     * Stop a specific migration job.
     */
    public void stopJob(String jobId) {
        String stopSQL = String.format("STOP MIGRATION '%s'", jobId);
        executeDistSQL(stopSQL);
        log.info("✓ Stopped migration job: {}", jobId);
    }
    
    /**
     * Execute a DistSQL command.
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
            throw new DataMigrationException("DistSQL execution failed: " + e.getMessage(), e);
        }
    }
}

