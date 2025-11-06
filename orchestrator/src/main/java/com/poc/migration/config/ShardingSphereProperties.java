package com.poc.migration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for ShardingSphere Proxy connection.
 */
@Configuration
@ConfigurationProperties(prefix = "shardingsphere.admin")
@Data
public class ShardingSphereProperties {
    
    /**
     * ShardingSphere Proxy host.
     */
    private String host = "shardingsphere-proxy";
    
    /**
     * ShardingSphere Proxy port.
     */
    private int port = 3307;
    
    /**
     * Admin database name.
     */
    private String database = "shardingsphere";
    
    /**
     * Migration database name.
     */
    private String migrationDb = "migration_db";
    
    /**
     * Admin username.
     */
    private String user = "root";
    
    /**
     * Admin password.
     */
    private String password = "root";
    
    /**
     * Database type for ShardingSphere Proxy protocol (mysql or postgresql).
     * Defaults to mysql.
     */
    private String type = "mysql";
}


