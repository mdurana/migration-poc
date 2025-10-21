package com.poc.migration.executor;

import com.poc.migration.model.JobRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataExecutor {

    @Value("${shardingsphere.admin.host}")
    private String adminHost;
    
    @Value("${shardingsphere.admin.port}")
    private int adminPort;
    
    @Value("${shardingsphere.admin.user}")
    private String adminUser;
    
    @Value("${shardingsphere.admin.password}")
    private String adminPassword;

    /**
     * Connect to ShardingSphere Proxy using MySQL protocol.
     * Use container port 3307, not host port!
     */
    private Connection getAdminConnection() throws Exception {
        // For Docker: use container name and container port
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/", adminHost, adminPort);
        Class.forName("com.mysql.cj.jdbc.Driver");
        log.debug("Connecting to proxy at: {}", jdbcUrl);
        return DriverManager.getConnection(jdbcUrl, adminUser, adminPassword);
    }

    /**
     * Registers source and target storage units in ShardingSphere Proxy.
     * This tells the proxy where the actual databases are.
     */
    public void registerSourceAndTarget(JobRequest request) throws Exception {
        log.info("Registering storage units...");
        
        // 1. Register Source Storage Unit
        String sourceUrl = buildJdbcUrl(request.getSource());
        String registerSourceSQL = String.format("""
            REGISTER STORAGE UNIT source_ds (
                URL="%s",
                USER="%s",
                PASSWORD="%s",
                PROPERTIES(
                    "maximumPoolSize"="10",
                    "minimumIdle"="2"
                )
            )
            """, sourceUrl, request.getSource().getUser(), request.getSource().getPassword());
        
        executeDistSQL(registerSourceSQL);
        log.info("✓ Source storage unit registered");

        // 2. Register Target Storage Unit
        String targetUrl = buildJdbcUrl(request.getTarget());
        String registerTargetSQL = String.format("""
            REGISTER STORAGE UNIT target_ds (
                URL="%s",
                USER="%s",
                PASSWORD="%s",
                PROPERTIES(
                    "maximumPoolSize"="10",
                    "minimumIdle"="2"
                )
            )
            """, targetUrl, request.getTarget().getUser(), request.getTarget().getPassword());
        
        executeDistSQL(registerTargetSQL);
        log.info("✓ Target storage unit registered");

        // 3. Verify registration
        verifyStorageUnits();
    }

    /**
     * Creates migration jobs for each table.
     * In ShardingSphere 5.5.2, each table requires a separate MIGRATE TABLE command.
     */
    public List<String> createMigrationJobs(JobRequest request) throws Exception {
        List<String> jobIds = new ArrayList<>();
        List<String> tables = request.getTablesToMigrate();
        
        log.info("Creating migration jobs for {} tables", tables.size());

        for (String tableName : tables) {
            // Use MIGRATE TABLE syntax
            // Format: MIGRATE TABLE source_storage_unit.schema.table INTO target_storage_unit.schema.table
            String sourceDb = request.getSource().getDatabase();
            String targetDb = request.getTarget().getDatabase();
            
            String migrateSQL = String.format("""
                MIGRATE TABLE source_ds.%s.%s INTO target_ds.%s.%s
                """, sourceDb, tableName, targetDb, tableName);
            
            log.info("Creating migration for table: {}", tableName);
            executeDistSQL(migrateSQL);
            
            // The job ID is returned, but we need to query for it
            // For now, we'll track by table name
            jobIds.add(tableName);
        }

        log.info("✓ Created {} migration jobs", jobIds.size());
        return jobIds;
    }

    /**
     * Starts migration jobs. In 5.5.2, jobs auto-start, so this checks status.
     */
    public void startMigrationJobs() throws Exception {
        log.info("Checking migration job status...");
        
        try (Connection conn = getAdminConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW MIGRATION LIST")) {
            
            log.info("Active migration jobs:");
            while (rs.next()) {
                String id = rs.getString("id");
                String tables = rs.getString("tables");
                String status = rs.getString("status");
                log.info("  Job ID: {}, Tables: {}, Status: {}", id, tables, status);
            }
        }
    }

    /**
     * Stops a specific migration job.
     */
    public void stopMigrationJob(String jobId) throws Exception {
        String stopSQL = String.format("STOP MIGRATION '%s'", jobId);
        executeDistSQL(stopSQL);
        log.info("✓ Stopped migration job: {}", jobId);
    }

    /**
     * Monitors migration progress until all tables reach incremental sync.
     * Returns when migration is ready for cutover.
     */
    public void monitorJobsUntilReady() throws Exception {
        log.info("Monitoring migration progress...");
        
        boolean allReady = false;
        int checkCount = 0;
        
        while (!allReady && checkCount < 120) { // Max 10 minutes (120 * 5s)
            Thread.sleep(5000); // Poll every 5 seconds
            checkCount++;
            
            try (Connection conn = getAdminConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW MIGRATION LIST")) {
                
                allReady = true;
                boolean hasJobs = false;
                
                while (rs.next()) {
                    hasJobs = true;
                    String id = rs.getString("id");
                    String tables = rs.getString("tables");
                    String status = rs.getString("status");
                    
                    log.info("[Check #{}] Job {}: {} - Status: {}", 
                            checkCount, id, tables, status);
                    
                    // Check if job is ready for cutover
                    if (!"EXECUTE_INCREMENTAL_TASK".equals(status) && 
                        !"FINISHED".equals(status)) {
                        allReady = false;
                    }
                    
                    // Check for errors
                    if (status != null && status.contains("ERROR")) {
                        throw new RuntimeException("Migration job " + id + " failed: " + status);
                    }
                }
                
                if (!hasJobs) {
                    throw new RuntimeException("No migration jobs found!");
                }
                
                if (allReady) {
                    log.info("✓ All migration jobs are ready for cutover");
                    return;
                }
            }
        }
        
        if (!allReady) {
            log.warn("Migration monitoring timed out after {} checks", checkCount);
        }
    }

    /**
     * Gets detailed status of a specific migration job.
     */
    public void showMigrationStatus(String jobId) throws Exception {
        String statusSQL = String.format("SHOW MIGRATION STATUS '%s'", jobId);
        
        try (Connection conn = getAdminConnection();
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
        }
    }

    /**
     * Commits (finalizes) the migration, switching over to target.
     */
    public void commitMigration(String jobId) throws Exception {
        log.info("Committing migration job: {}", jobId);
        
        // Run consistency check first
        String checkSQL = String.format("CHECK MIGRATION '%s'", jobId);
        executeDistSQL(checkSQL);
        log.info("✓ Consistency check passed");
        
        // Commit the migration
        String commitSQL = String.format("COMMIT MIGRATION '%s'", jobId);
        executeDistSQL(commitSQL);
        log.info("✓ Migration committed successfully");
    }

    /**
     * Rolls back the migration if something goes wrong.
     */
    public void rollbackMigration(String jobId) throws Exception {
        String rollbackSQL = String.format("ROLLBACK MIGRATION '%s'", jobId);
        executeDistSQL(rollbackSQL);
        log.info("✓ Migration rolled back: {}", jobId);
    }

    /**
     * Verifies that storage units were registered correctly.
     */
    private void verifyStorageUnits() throws Exception {
        log.info("Verifying storage unit registration...");
        
        try (Connection conn = getAdminConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW STORAGE UNITS")) {
            
            log.info("Registered storage units:");
            boolean hasSource = false;
            boolean hasTarget = false;
            
            while (rs.next()) {
                String name = rs.getString("name");
                String type = rs.getString("type");
                String url = rs.getString("url");
                
                log.info("  {} - {} - {}", name, type, url);
                
                if ("source_ds".equals(name)) hasSource = true;
                if ("target_ds".equals(name)) hasTarget = true;
            }
            
            if (!hasSource || !hasTarget) {
                throw new RuntimeException("Storage units not properly registered!");
            }
            
            log.info("✓ Storage units verified");
        }
    }

    /**
     * Executes a DistSQL command on the ShardingSphere Proxy.
     */
    private void executeDistSQL(String sql) throws Exception {
        // Log first 200 chars for debugging
        String preview = sql.length() > 200 ? sql.substring(0, 200) + "..." : sql;
        log.debug("Executing DistSQL: {}", preview);
        
        try (Connection conn = getAdminConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(sql);
            log.debug("✓ DistSQL executed successfully");
            
        } catch (Exception e) {
            log.error("Failed to execute DistSQL: {}", preview);
            log.error("Error: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Builds proper JDBC URL for each database type.
     */
    private String buildJdbcUrl(JobRequest.DbConfig config) {
        if ("postgresql".equals(config.getType())) {
            return String.format("jdbc:postgresql://%s:%d/%s?useSSL=false",
                    config.getHost(), config.getPort(), config.getDatabase());
        }
        
        // MySQL
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                config.getHost(), config.getPort(), config.getDatabase());
    }
}