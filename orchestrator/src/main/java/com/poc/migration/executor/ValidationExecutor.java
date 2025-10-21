package com.poc.migration.executor;

import com.poc.migration.model.JobRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ValidationExecutor {

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

        // Build JDBC URLs with proper parameters for each database type
        String sourceJdbc = buildJdbcUrl(source);
        String targetJdbc = buildJdbcUrl(target);
        
        log.info("Source: {} ({})", sourceJdbc, source.getType());
        log.info("Target: {} ({})", targetJdbc, target.getType());

        // Store results for summary
        Map<String, ValidationResult> results = new HashMap<>();

        for (String table : request.getTablesToMigrate()) {
            try {
                log.info("Validating table: {}", table);
                
                // Get counts from both databases
                long sourceCount = getRowCount(
                    sourceJdbc, 
                    source.getUser(), 
                    source.getPassword(), 
                    source.getDatabase(),
                    table,
                    source.getType()
                );
                
                long targetCount = getRowCount(
                    targetJdbc, 
                    target.getUser(), 
                    target.getPassword(), 
                    target.getDatabase(),
                    table,
                    target.getType()
                );
                
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
    private long getRowCount(
            String jdbcUrl, 
            String user, 
            String password, 
            String databaseName,
            String tableName,
            String dbType
    ) throws Exception {
        
        // Validate table name to prevent SQL injection
        if (!isValidTableName(tableName)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }

        // Build the SQL query with proper schema/database qualification
        String sql = buildCountQuery(tableName, databaseName, dbType);
        
        log.debug("Executing: {}", sql);
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // Set query timeout to prevent hanging
            stmt.setQueryTimeout(30); // 30 seconds
            
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
            throw new Exception("Database query failed for table " + tableName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Builds the COUNT query with proper schema qualification.
     */
    private String buildCountQuery(String tableName, String databaseName, String dbType) {
        if ("postgresql".equals(dbType)) {
            // PostgreSQL uses schema.table format
            // Default schema is 'public'
            return String.format("SELECT COUNT(*) FROM public.\"%s\"", tableName);
        } else {
            // MySQL uses database.table format
            // Use backticks for identifier quoting in MySQL
            return String.format("SELECT COUNT(*) FROM `%s`.`%s`", databaseName, tableName);
        }
    }

    /**
     * Validates table name to prevent SQL injection.
     * Allows: letters, numbers, underscores, and hyphens
     */
    private boolean isValidTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return false;
        }
        
        // Allow alphanumeric, underscore, and hyphen
        // Disallow spaces, semicolons, quotes, etc.
        return tableName.matches("^[a-zA-Z0-9_-]+$");
    }

    /**
     * Builds proper JDBC URL for each database type.
     */
    private String buildJdbcUrl(JobRequest.DbConfig config) {
        if ("postgresql".equals(config.getType())) {
            // PostgreSQL-specific URL (no useSSL parameter)
            return String.format("jdbc:postgresql://%s:%d/%s",
                    config.getHost(), config.getPort(), config.getDatabase());
        } else if ("mysql".equals(config.getType())) {
            // MySQL-specific URL with common parameters
            return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    config.getHost(), config.getPort(), config.getDatabase());
        } else {
            // Generic fallback
            return String.format("jdbc:%s://%s:%d/%s",
                    config.getType(), config.getHost(), config.getPort(), config.getDatabase());
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

        String sourceJdbc = buildJdbcUrl(source);
        String targetJdbc = buildJdbcUrl(target);

        // Reuse connections
        try (Connection sourceConn = DriverManager.getConnection(
                    sourceJdbc, source.getUser(), source.getPassword());
            Connection targetConn = DriverManager.getConnection(
                    targetJdbc, target.getUser(), target.getPassword())) {
            
            boolean allValid = true;
            
            for (String table : request.getTablesToMigrate()) {
                try {
                    long sourceCount = getRowCountWithConnection(
                        sourceConn, table, source.getDatabase(), source.getType());
                    long targetCount = getRowCountWithConnection(
                        targetConn, table, target.getDatabase(), target.getType());
                    
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
            Connection conn, String table, String database, String dbType) 
            throws SQLException {
        
        if (!isValidTableName(table)) {
            throw new IllegalArgumentException("Invalid table name: " + table);
        }
        
        String sql = buildCountQuery(table, database, dbType);
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setQueryTimeout(30);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                throw new SQLException("No result from count query");
            }
        }
    }

}