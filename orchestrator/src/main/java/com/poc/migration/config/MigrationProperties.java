package com.poc.migration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for migration operations.
 */
@Configuration
@ConfigurationProperties(prefix = "migration")
@Data
public class MigrationProperties {
    
    /**
     * Schema generation and output configuration.
     */
    private SchemaConfig schema = new SchemaConfig();
    
    /**
     * Monitoring configuration.
     */
    private MonitoringConfig monitoring = new MonitoringConfig();
    
    /**
     * Retry configuration.
     */
    private RetryConfig retry = new RetryConfig();
    
    @Data
    public static class SchemaConfig {
        /**
         * Output directory for generated schema files.
         */
        private String outputDir = "/app/generated-schema/";
    }
    
    @Data
    public static class MonitoringConfig {
        /**
         * Interval between migration status checks (milliseconds).
         */
        private long checkIntervalMs = 5000;
        
        /**
         * Maximum number of status checks before timeout.
         */
        private int maxChecks = 120;
        
        /**
         * Query timeout for validation queries (seconds).
         */
        private int queryTimeoutSeconds = 30;
    }
    
    @Data
    public static class RetryConfig {
        /**
         * Maximum retry attempts for transient failures.
         */
        private int maxAttempts = 5;
        
        /**
         * Delay between retry attempts (milliseconds).
         */
        private long delayMs = 2000;
    }
}


