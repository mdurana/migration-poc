package com.poc.migration.executor;

import com.poc.migration.config.MigrationProperties;
import com.poc.migration.exception.ValidationException;
import com.poc.migration.infrastructure.database.DatabaseConnectionConfig;
import com.poc.migration.infrastructure.database.DatabaseConnectionFactory;
import com.poc.migration.infrastructure.database.DatabaseType;
import com.poc.migration.model.JobRequest;
import com.poc.migration.util.SqlValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for validating migration results.
 * Refactored to use DatabaseConnectionFactory and SqlValidator.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ValidationExecutor {
    
    private final DatabaseConnectionFactory connectionFactory;
    private final MigrationProperties properties;

    /**
     * Validates that row counts match between source and target for all tables.
     * Returns true only if ALL tables have matching counts.
     */
    public boolean validateRowCounts(JobRequest request) {
        log.info("========== Starting Row Count Validation ==========");
        
        boolean allValid = true;
        int totalTables = request.getTablesToMigrate().size();
        int validTables = 0;
        
        JobRequest.DbConfig source = request.getSource();
        JobRequest.DbConfig target = request.getTarget();

        // Build connection configs
        DatabaseConnectionConfig sourceConfig = convertToConnectionConfig(source);
        DatabaseConnectionConfig targetConfig = convertToConnectionConfig(target);
        
        log.info("Source: {}:{}/{} ({})", source.getHost(), source.getPort(), source.getDatabase(), source.getType());
        log.info("Target: {}:{}/{} ({})", target.getHost(), target.getPort(), target.getDatabase(), target.getType());

        // Store results for summary
        Map<String, ValidationResult> results = new HashMap<>();

        for (String table : request.getTablesToMigrate()) {
            try {
                log.info("Validating table: {}", table);
                
                // Get counts from both databases
                long sourceCount = getRowCount(sourceConfig, table);
                long targetCount = getRowCount(targetConfig, table);
                
                // Compare counts
                boolean isValid = (sourceCount == targetCount);
                ValidationResult result = new ValidationResult(table, sourceCount, targetCount, isValid, null);
                results.put(table, result);
                
                if (isValid) {
                    log.info("  ✓ PASS - Table '{}': {} rows (source) == {} rows (target)", 
                            table, sourceCount, targetCount);
                    validTables++;
                } else {
                    log.error("  ✗ FAIL - Table '{}': {} rows (source) != {} rows (target). Diff: {}", 
                            table, sourceCount, targetCount, Math.abs(sourceCount - targetCount));
                    allValid = false;
                }
                
            } catch (Exception e) {
                log.error("  ✗ ERROR - Failed to validate table '{}': {}", table, e.getMessage(), e);
                results.put(table, new ValidationResult(table, -1, -1, false, e.getMessage()));
                allValid = false;
            }
        }

        // Print summary
        log.info("========== Validation Summary ==========");
        log.info("Total tables: {}", totalTables);
        log.info("Valid tables: {}", validTables);
        log.info("Failed tables: {}", totalTables - validTables);
        
        if (allValid) {
            log.info("Result: ✓ ALL VALIDATIONS PASSED");
        } else {
            log.error("Result: ✗ VALIDATION FAILED");
            log.error("Failed tables:");
            results.values().stream()
                .filter(r -> !r.isValid())
                .forEach(r -> log.error("  - {}: Source={}, Target={}, Error={}", 
                        r.tableName(), r.sourceCount(), r.targetCount(), r.error()));
        }
        
        log.info("========================================");
        
        return allValid;
    }

    /**
     * Gets the row count for a specific table using a prepared statement.
     * Handles schema qualification and SQL injection prevention.
     */
    private long getRowCount(DatabaseConnectionConfig config, String tableName) throws Exception {
        // Validate table name to prevent SQL injection
        SqlValidator.validateTableName(tableName);

        // Build the SQL query with proper schema/database qualification
        String sql = buildCountQuery(config, tableName);
        
        log.debug("Executing: {}", sql);
        
        try (Connection conn = connectionFactory.createConnection(config);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // Set query timeout from configuration
            stmt.setQueryTimeout(properties.getMonitoring().getQueryTimeoutSeconds());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long count = rs.getLong(1);
                    log.debug("  Count result: {}", count);
                    return count;
                } else {
                    throw new SQLException("No result returned from count query");
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get row count for table '{}': {}", tableName, e.getMessage());
            throw new ValidationException("Database query failed for table " + tableName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Builds the COUNT query with proper schema qualification.
     */
    private String buildCountQuery(DatabaseConnectionConfig config, String tableName) {
        if (config.getType() == DatabaseType.POSTGRESQL) {
            // PostgreSQL uses schema.table format
            String schema = config.getSchemaOrDefault();
            return String.format("SELECT COUNT(*) FROM %s.\"%s\"", schema, tableName);
        } else {
            // MySQL uses database.table format
            // Use backticks for identifier quoting in MySQL
            return String.format("SELECT COUNT(*) FROM `%s`.`%s`", config.getDatabase(), tableName);
        }
    }

    /**
     * Simple record to store validation results.
     */
    private record ValidationResult(
            String tableName,
            long sourceCount,
            long targetCount,
            boolean isValid,
            String error
    ) {}

    /**
     * Validates with connection reuse (better for many tables).
     */
    public boolean validateRowCountsOptimized(JobRequest request) {
        log.info("========== Starting Row Count Validation (Optimized) ==========");
        
        JobRequest.DbConfig source = request.getSource();
        JobRequest.DbConfig target = request.getTarget();

        DatabaseConnectionConfig sourceConfig = convertToConnectionConfig(source);
        DatabaseConnectionConfig targetConfig = convertToConnectionConfig(target);

        // Reuse connections
        try (Connection sourceConn = connectionFactory.createConnection(sourceConfig);
             Connection targetConn = connectionFactory.createConnection(targetConfig)) {
            
            boolean allValid = true;
            
            for (String table : request.getTablesToMigrate()) {
                try {
                    long sourceCount = getRowCountWithConnection(
                        sourceConn, table, sourceConfig);
                    long targetCount = getRowCountWithConnection(
                        targetConn, table, targetConfig);
                    
                    if (sourceCount != targetCount) {
                        log.error("✗ Table '{}': {} != {}", table, sourceCount, targetCount);
                        allValid = false;
                    } else {
                        log.info("✓ Table '{}': {} rows", table, sourceCount);
                    }
                } catch (Exception e) {
                    log.error("✗ Failed to validate '{}': {}", table, e.getMessage());
                    allValid = false;
                }
            }
            
            return allValid;
            
        } catch (SQLException e) {
            log.error("Failed to establish database connections: {}", e.getMessage());
            return false;
        }
    }

    private long getRowCountWithConnection(
            Connection conn, String table, DatabaseConnectionConfig config) 
            throws SQLException {
        
        SqlValidator.validateTableName(table);
        
        String sql = buildCountQuery(config, table);
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setQueryTimeout(properties.getMonitoring().getQueryTimeoutSeconds());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                throw new SQLException("No result from count query");
            }
        }
    }
    
    /**
     * Convert JobRequest.DbConfig to DatabaseConnectionConfig.
     */
    private DatabaseConnectionConfig convertToConnectionConfig(JobRequest.DbConfig dbConfig) {
        return DatabaseConnectionConfig.builder()
            .type(DatabaseType.fromString(dbConfig.getType()))
            .host(dbConfig.getHost())
            .port(dbConfig.getPort())
            .database(dbConfig.getDatabase())
            .schema(dbConfig.getSchema())
            .user(dbConfig.getUser())
            .password(dbConfig.getPassword())
            .build();
    }
}