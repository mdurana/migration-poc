package com.poc.migration.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for database-related beans.
 */
@Configuration
public class DatabaseConfiguration {
    
    /**
     * ObjectMapper for JSON serialization/deserialization.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .findAndRegisterModules(); // Register Java 8 time module, etc.
    }
}


