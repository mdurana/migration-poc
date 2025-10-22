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

import org.apache.commons.lang3.StringUtils;

import jakarta.annotation.PostConstruct;

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

    @Value("${shardingsphere.admin.database:shardingsphere}")
    private String adminDatabase;

    @Value("${shardingsphere.admin.migration-db:migration_db}")
    private String migrationDatabase;

    /**
     * Ensures the logical migration database exists on application startup.
     * This runs ONCE when the Spring context initializes.
     */
    @PostConstruct
    public void ensureMigrationDatabase() {
        try {
            log.info("Ensuring logical migration database exists: {}", migrationDatabase);
            
            // Step 1: Check if database already exists
            boolean databaseExists = checkDatabaseExists();
            
            if (databaseExists) {
                log.info("✓ Migration database '{}' already exists", migrationDatabase);
            } else {
                // Step 2: Create the database using admin database context
                log.info("Creating migration database: {}", migrationDatabase);
                String createDbSQL = String.format("CREATE DATABASE IF NOT EXISTS %s", migrationDatabase);
                executeSQL(createDbSQL, adminDatabase);
                log.info("✓ Database creation command executed");
                
                // Step 3: Wait for database to be ready
                Thread.sleep(1000);
            }
            
            // Step 4: Verify we can connect to the migration database
            int retries = 5;
            boolean connected = false;
            
            for (int i = 0; i < retries; i++) {
                try {
                    try (Connection testConn = getAdminConnection(migrationDatabase)) {
                        log.info("✓ Successfully connected to migration database: {}", migrationDatabase);
                        connected = true;
                        break;
                    }
                } catch (Exception e) {
                    if (i < retries - 1) {
                        log.warn("Attempt {}/{} - Migration database not ready yet, retrying in 2s...", 
                                i + 1, retries);
                        Thread.sleep(2000);
                    } else {
                        log.error("Failed to connect to migration database after {} attempts", retries);
                        throw e;
                    }
                }
            }
            
            if (!connected) {
                throw new RuntimeException("Could not establish connection to migration database: " + migrationDatabase);
            }
            
        } catch (Exception e) {
            log.error("Failed to ensure migration database: {}", e.getMessage(), e);
            throw new RuntimeException("Migration database setup failed", e);
        }
    }

    /**
     * Checks if the migration database already exists.
     */
    private boolean checkDatabaseExists() {
        try (Connection conn = getAdminConnection(adminDatabase);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
            
            while (rs.next()) {
                String dbName = rs.getString(1);
                if (migrationDatabase.equals(dbName)) {
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
     * Connect to ShardingSphere Proxy using MySQL protocol.
     * Use container port 3307, not host port!
     */
    private Connection getAdminConnection(String db) throws Exception {
        // For Docker: use container name and container port
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s", adminHost, adminPort, db);
        Class.forName("com.mysql.cj.jdbc.Driver");
        log.debug("Connecting to proxy at: {}", jdbcUrl);
        return DriverManager.getConnection(jdbcUrl, adminUser, adminPassword);
    }

    /**
     * Registers source and target storage units in ShardingSphere Proxy.
     * This tells the proxy where the actual databases are.
     */
    public void registerSourceAndTarget(JobRequest request) throws Exception {
        log.info("Registering storage units in database: {}", migrationDatabase);
        
        // 1. Register Source Storage Unit
        String sourceUrl = buildJdbcUrl(request.getSource());
        String registerSourceSQL = String.format("""
            REGISTER MIGRATION SOURCE STORAGE UNIT source_ds (
                URL="%s",
                USER="%s",
                PASSWORD="%s",
                PROPERTIES(
                    "maximumPoolSize"=10,
                    "idleTimeout"=30000
                )
            )
            """, sourceUrl, request.getSource().getUser(), request.getSource().getPassword());
        
        executeMigrationSQL(registerSourceSQL);
        log.info("✓ Source storage unit registered");

        // 2. Register Target Storage Unit
        String targetUrl = buildJdbcUrl(request.getTarget());
        String registerTargetSQL = String.format("""
            REGISTER STORAGE UNIT target_ds (
                URL="%s",
                USER="%s",
                PASSWORD="%s",
                PROPERTIES(
                    "maximumPoolSize"=10,
                    "idleTimeout"=30000
                )
            )
            """, targetUrl, request.getTarget().getUser(), request.getTarget().getPassword());
        
        executeMigrationSQL(registerTargetSQL);
        log.info("✓ Target storage unit registered");

        // 3. Verify registration
        verifyStorageUnits();
    }

    /**
     * Creates migration jobs for each table.
     * In ShardingSphere 5.5.2, each table requires a separate MIGRATE TABLE command.
     * 
     * IMPORTANT: The database is already specified in the storage unit's JDBC URL.
     * Syntax:
     *   - MySQL: MIGRATE TABLE source_ds.table_name INTO target_ds.table_name
     *   - PostgreSQL: MIGRATE TABLE source_ds.schema.table_name INTO target_ds.schema.table_name
     */
    public List<String> createMigrationJobs(JobRequest request) throws Exception {
        List<String> jobIds = new ArrayList<>();
        List<String> tables = request.getTablesToMigrate();
        
        log.info("Creating migration jobs for {} tables", tables.size());

        for (String tableName : tables) {
            String migrateSQL;
            
            // Build MIGRATE TABLE command based on database type
            if ("postgresql".equalsIgnoreCase(request.getTarget().getType())) {
                // PostgreSQL requires schema qualification
                String sourceSchema = request.getSource().getSchemaOrDefault();
                String targetSchema = request.getTarget().getSchemaOrDefault();
                
                migrateSQL = String.format(
                    "MIGRATE TABLE source_ds.%s.%s INTO %s.%s",
                    sourceSchema, tableName, targetSchema, tableName
                );
            } else {
                // MySQL: storage_unit.table_name
                // Database is already in the JDBC URL of the storage unit
                migrateSQL = String.format(
                    "MIGRATE TABLE source_ds.%s INTO %s",
                    tableName, tableName
                );
            }
            
            log.info("Creating migration for table: {}", tableName);
            log.debug("Migration command: {}", migrateSQL);
            
            executeMigrationSQL(migrateSQL);
            
            // Track by table name for now
            // Actual job IDs will be retrieved via SHOW MIGRATION LIST
            jobIds.add(tableName);
        }

        log.info("✓ Created {} migration jobs", jobIds.size());
        
        // Give jobs a moment to register
        Thread.sleep(2000);
        
        // Return actual job IDs from ShardingSphere
        return getActualJobIds();
    }

    /**
     * Retrieves actual job IDs from ShardingSphere after job creation.
     */
    private List<String> getActualJobIds() throws Exception {
        List<String> actualJobIds = new ArrayList<>();
        
        try (Connection conn = getAdminConnection(migrationDatabase);
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
        }
        
        log.info("✓ Retrieved {} actual job IDs from ShardingSphere", actualJobIds.size());
        return actualJobIds;
    }

    /**
     * Starts migration jobs. In 5.5.2, jobs auto-start, so this checks status.
     */
    public void startMigrationJobs() throws Exception {
        log.info("Checking migration job status...");
        
        try (Connection conn = getAdminConnection(migrationDatabase);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW MIGRATION LIST")) {
            
            log.info("Active migration jobs:");
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
        }
    }

    /**
     * Stops a specific migration job.
     */
    public void stopMigrationJob(String jobId) throws Exception {
        String stopSQL = String.format("STOP MIGRATION '%s'", jobId);
        executeMigrationSQL(stopSQL);
        log.info("✓ Stopped migration job: {}", jobId);
    }

    /**
     * Monitors migration progress until all tables reach incremental sync.
     * Returns when migration is ready for cutover.
     */
    public void monitorJobsUntilReady() throws Exception {
        log.info("Monitoring migration progress...");
        
        List<String> migrationJobIds = new ArrayList<>();
            
        try (Connection conn = getAdminConnection(migrationDatabase);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SHOW MIGRATION LIST")) {
            
            while (rs.next()) {
                String id = rs.getString("id");
                migrationJobIds.add(id);
            }
        }
        
        boolean allReady = false;
        int checkCount = 0;
        final int maxChecks = 120; // Max 10 minutes (120 * 5s) for POC purposes only
        
        int readyCount = 0;
        int totalJobs = 0;

        while(!allReady && checkCount < maxChecks) {
            checkCount++;
            Thread.sleep(5000); // 5 seconds between checks
            log.info("Migration status check #{}/{}", checkCount, maxChecks);

            for (String jobId : migrationJobIds) {
                log.info("Checking status for migration job: {}", jobId);

                String statusSQL = String.format("SHOW MIGRATION STATUS '%s'", jobId);

                try (Connection conn = getAdminConnection(migrationDatabase);
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(statusSQL)) {
                    
                    while (rs.next()) {
                        String status = rs.getString("status");

                        // Check if job is ready for cutover
                        if ("EXECUTE_INCREMENTAL_TASK".equals(status) || "FINISHED".equals(status)) {
                            readyCount++;
                        }
                        
                        // Check for errors
                        if (status != null && (status.contains("ERROR") || status.contains("FAILED"))) {
                            throw new RuntimeException("Migration job " + jobId + " failed with status: " + status);
                        }
                        totalJobs++;
                    }
                }
            }
            // TODO : Improve ready check logic
            allReady = (readyCount == totalJobs);

            if (allReady) {
                log.info("✓ All {} migration jobs are ready for cutover ({}/{})", 
                        totalJobs, readyCount, totalJobs);
                return;
            } else {
                log.debug("Progress: {}/{} jobs ready for cutover", readyCount, totalJobs);
            }
        }

        if (!allReady) {
            throw new RuntimeException(
                "Migration monitoring timed out after " + checkCount + 
                " checks (" + (checkCount * 5) + " seconds). " +
                "Jobs may still be running - check ShardingSphere Proxy logs."
            );
        }

    }

    /**
     * Gets detailed status of a specific migration job.
     */
    public void showMigrationStatus(String jobId) throws Exception {
        String statusSQL = String.format("SHOW MIGRATION STATUS '%s'", jobId);
        
        try (Connection conn = getAdminConnection(migrationDatabase);
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
     * Commits (finalizes) the migration, switching over to target.
     */
    public void commitMigration(String jobId) throws Exception {
        log.info("Committing migration job: {}", jobId);
        
        // Run consistency check first
        try {
            String checkSQL = String.format("CHECK MIGRATION '%s'", jobId);
            executeMigrationSQL(checkSQL);
            log.info("✓ Consistency check passed for job: {}", jobId);
        } catch (Exception e) {
            log.warn("Consistency check failed or not supported: {}", e.getMessage());
            log.warn("Proceeding with commit anyway...");
        }
        
        // Commit the migration
        String commitSQL = String.format("COMMIT MIGRATION '%s'", jobId);
        executeMigrationSQL(commitSQL);
        log.info("✓ Migration committed successfully: {}", jobId);
    }

    /**
     * Rolls back the migration if something goes wrong.
     */
    public void rollbackMigration(String jobId) throws Exception {
        try {
            String rollbackSQL = String.format("ROLLBACK MIGRATION '%s'", jobId);
            executeMigrationSQL(rollbackSQL);
            log.info("✓ Migration rolled back: {}", jobId);
        } catch (Exception e) {
            log.error("Failed to rollback migration {}: {}", jobId, e.getMessage());
            // Don't rethrow - best effort rollback
        }
    }

    /**
     * Verifies that storage units were registered correctly.
     */
    private void verifyStorageUnits() throws Exception {
        log.info("Verifying source storage unit registration...");
        
        try (Connection conn = getAdminConnection(migrationDatabase);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW MIGRATION SOURCE STORAGE UNITS")) {
            
            log.info("Registered storage units:");
            boolean hasSource = false;
            
            while (rs.next()) {
                String name = rs.getString("name");
                String type = rs.getString("type");
                String host = rs.getString("host");
                String port = rs.getString("port");
                String db = rs.getString("db");
                
                log.info("  {} - {} - {}:{}/{}", name, type, host, port, db);
                
                if ("source_ds".equals(name)) hasSource = true;
            }
            
            if (!hasSource) {
                throw new RuntimeException("Storage units not properly registered!");
            }
            
            log.info("✓ Storage units verified");
        }

        try (Connection conn = getAdminConnection(migrationDatabase);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW STORAGE UNITS")) {
            
            log.info("Registered target storage units:");
            boolean hasTarget = false;
            
            while (rs.next()) {
                String name = rs.getString("name");
                String type = rs.getString("type");
                String host = rs.getString("host");
                String port = rs.getString("port");
                String db = rs.getString("db");
                
                log.info("  {} - {} - {}:{}/{}", name, type, host, port, db);
                
                if ("target_ds".equals(name)) hasTarget = true;
            }
            
            if (!hasTarget) {
                throw new RuntimeException("Storage units not properly registered!");
            }
            
            log.info("✓ Storage units verified");
        }
    }

    /**
     * Executes a DistSQL command on the ShardingSphere Proxy.
     */
    private void executeSQL(String sql, String db) throws Exception {
        // Log first 200 chars for debugging
        String preview = sql.length() > 200 ? sql.substring(0, 200) + "..." : sql;
        log.debug("Executing DistSQL on database '{}': {}", db, preview);
        
        try (Connection conn = getAdminConnection(db);
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(sql);
            log.debug("✓ DistSQL executed successfully on database '{}'", db);
            
        } catch (Exception e) {
            log.error("Failed to execute DistSQL on database '{}': {}", db, preview);
            log.error("Error: {}", e.getMessage());
            throw e;
        }
    }

    private void executeMigrationSQL(String sql) throws Exception {
        // Log first 200 chars for debugging
       executeSQL(sql, migrationDatabase);
    }

    /**
     * Builds proper JDBC URL for each database type with the database included.
     */
    private String buildJdbcUrl(JobRequest.DbConfig config) {
        if ("postgresql".equals(config.getType())) {
            // PostgreSQL URL with database
            return String.format("jdbc:postgresql://%s:%d/%s",
                    config.getHost(), config.getPort(), config.getDatabase());
        }
        
        // MySQL URL with database (no useSSL for internal Docker network)
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                config.getHost(), config.getPort(), config.getDatabase());
    }
}