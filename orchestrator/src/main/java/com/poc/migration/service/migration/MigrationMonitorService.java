package com.poc.migration.service.migration;

import com.poc.migration.config.MigrationProperties;
import com.poc.migration.exception.DataMigrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * Service for monitoring migration job progress.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MigrationMonitorService {
    
    private final ShardingSphereConnectionService connectionService;
    private final MigrationProperties properties;
    
    /**
     * Monitor migration jobs until all are ready for cutover.
     * Returns when all jobs reach incremental sync phase.
     */
    public void monitorJobsUntilReady(List<String> jobIds) {
        log.info("Monitoring {} migration jobs...", jobIds.size());
        
        boolean allReady = false;
        int checkCount = 0;
        int maxChecks = properties.getMonitoring().getMaxChecks();
        long checkInterval = properties.getMonitoring().getCheckIntervalMs();
        
        while (!allReady && checkCount < maxChecks) {
            checkCount++;
            
            try {
                Thread.sleep(checkInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DataMigrationException("Migration monitoring interrupted", e);
            }
            
            log.info("Migration status check #{}/{}", checkCount, maxChecks);
            
            int readyCount = 0;
            int totalJobs = 0;
            
            for (String jobId : jobIds) {
                try {
                    JobStatus status = getJobStatus(jobId);
                    
                    if (status.isReady()) {
                        readyCount++;
                    }
                    
                    if (status.isError()) {
                        throw new DataMigrationException(
                            "Migration job " + jobId + " failed with status: " + status.getStatus()
                        );
                    }
                    
                    totalJobs++;
                    
                } catch (DataMigrationException e) {
                    throw e; // Rethrow migration exceptions
                } catch (Exception e) {
                    log.warn("Could not check status for job {}: {}", jobId, e.getMessage());
                }
            }
            
            allReady = (readyCount == totalJobs && totalJobs > 0);
            
            if (allReady) {
                log.info("✓ All {} migration jobs are ready for cutover ({}/{})", 
                        totalJobs, readyCount, totalJobs);
                return;
            } else {
                log.debug("Progress: {}/{} jobs ready for cutover", readyCount, totalJobs);
            }
        }
        
        if (!allReady) {
            throw new DataMigrationException(
                "Migration monitoring timed out after " + checkCount + 
                " checks (" + (checkCount * checkInterval / 1000) + " seconds). " +
                "Jobs may still be running - check ShardingSphere Proxy logs."
            );
        }
    }
    
    /**
     * Get status of a specific job.
     */
    private JobStatus getJobStatus(String jobId) {
        String statusSQL = String.format("SHOW MIGRATION STATUS '%s'", jobId);
        
        try (Connection conn = connectionService.getMigrationConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(statusSQL)) {
            
            if (rs.next()) {
                String status = rs.getString("status");
                return new JobStatus(jobId, status);
            }
            
            throw new DataMigrationException("No status found for job: " + jobId);
            
        } catch (Exception e) {
            throw new DataMigrationException("Failed to get status for job: " + jobId, e);
        }
    }
    
    /**
     * Inner class to hold job status information.
     */
    private static class JobStatus {
        private final String status;
        
        JobStatus(String jobId, String status) {
            this.status = status;
        }
        
        String getStatus() {
            return status;
        }
        
        boolean isReady() {
            return "EXECUTE_INCREMENTAL_TASK".equals(status) || "FINISHED".equals(status);
        }
        
        boolean isError() {
            return status != null && (status.contains("ERROR") || status.contains("FAILED"));
        }
    }
}

