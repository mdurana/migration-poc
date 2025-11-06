package com.poc.migration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for creating a migration job.
 * Maps to job-template.json structure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobRequest {
    
    @NotBlank(message = "Job name is required")
    private String jobName;
    
    @NotNull(message = "Source database configuration is required")
    @Valid
    private DbConfig source;
    
    @NotNull(message = "Target database configuration is required")
    @Valid
    private DbConfig target;
    
    @NotEmpty(message = "At least one table must be specified for migration")
    private List<String> tablesToMigrate;
    
    /**
     * Optional data type mappings for heterogeneous migrations.
     * Format: "source_type.SOURCE_TYPE" -> "TARGET_TYPE"
     * Example: "mysql.TINYINT" -> "SMALLINT"
     */
    private Map<String, String> dataTypeMappings;

    /**
     * Database configuration.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DbConfig {
        
        @NotBlank(message = "Database type is required (e.g., 'mysql', 'postgresql')")
        private String type;
        
        @NotBlank(message = "Database host is required")
        private String host;
        
        @Positive(message = "Port must be a positive number")
        private int port;
        
        @NotBlank(message = "Database name is required")
        private String database;
        
        @NotBlank(message = "Database user is required")
        private String user;
        
        @NotBlank(message = "Database password is required")
        private String password;
        
        /**
         * Schema name (optional, primarily for PostgreSQL).
         * Defaults to 'public' for PostgreSQL if not specified.
         */
        private String schema;
        
        /**
         * Storage unit name for ShardingSphere registration.
         * Optional - defaults to "source_ds" for source and "target_ds" for target.
         */
        private String storageUnitName;
        
        /**
         * Helper to get the schema name with a default.
         */
        public String getSchemaOrDefault() {
            if (schema != null && !schema.isEmpty()) {
                return schema;
            }
            // Return default schema based on database type
            return "postgresql".equalsIgnoreCase(type) ? "public" : database;
            //return database;
        }
        
        /**
         * Helper to get the storage unit name with a default.
         */
        public String getStorageUnitNameOrDefault(String defaultName) {
            if (storageUnitName != null && !storageUnitName.isEmpty()) {
                return storageUnitName;
            }
            return defaultName;
        }
    }
}