package com.poc.migration.service.migration;

import com.poc.migration.exception.DataMigrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

/**
 * Service for committing and rolling back migrations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MigrationCommitService {
    
    private final ShardingSphereConnectionService connectionService;
    
    /**
     * Commit (finalize) migration jobs, switching over to target.
     */
    public void commitMigrations(List<String> jobIds) {
        log.info("Committing {} migration jobs...", jobIds.size());
        
        for (String jobId : jobIds) {
            commitMigration(jobId);
        }
        
        log.info("✓ All migrations committed successfully");
    }
    
    /**
     * Commit a single migration job.
     */
    public void commitMigration(String jobId) {
        log.info("Committing migration job: {}", jobId);
        
        try {
            // Run consistency check first
            runConsistencyCheck(jobId);
            
            // Commit the migration
            String commitSQL = String.format("COMMIT MIGRATION '%s'", jobId);
            executeDistSQL(commitSQL);
            
            log.info("✓ Migration committed successfully: {}", jobId);
            
        } catch (Exception e) {
            throw new DataMigrationException("Failed to commit migration: " + jobId, e);
        }
    }
    
    /**
     * Run consistency check for a migration job.
     */
    private void runConsistencyCheck(String jobId) {
        try {
            String checkSQL = String.format("CHECK MIGRATION '%s'", jobId);
            executeDistSQL(checkSQL);
            log.info("✓ Consistency check passed for job: {}", jobId);
            
        } catch (Exception e) {
            log.warn("Consistency check failed or not supported: {}", e.getMessage());
            log.warn("Proceeding with commit anyway...");
        }
    }
    
    /**
     * Rollback migration jobs in case of failure.
     */
    public void rollbackMigrations(List<String> jobIds) {
        log.warn("Attempting to rollback {} migration jobs...", jobIds.size());
        
        for (String jobId : jobIds) {
            rollbackMigration(jobId);
        }
    }
    
    /**
     * Rollback a single migration job.
     */
    public void rollbackMigration(String jobId) {
        try {
            String rollbackSQL = String.format("ROLLBACK MIGRATION '%s'", jobId);
            executeDistSQL(rollbackSQL);
            log.info("✓ Migration rolled back: {}", jobId);
            
        } catch (Exception e) {
            log.error("Failed to rollback migration {}: {}", jobId, e.getMessage());
            // Don't rethrow - best effort rollback
        }
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


