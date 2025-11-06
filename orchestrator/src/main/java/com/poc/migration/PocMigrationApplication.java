package com.poc.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Main application class for the Migration Orchestrator.
 * @EnableAsync is now configured in AsyncConfiguration.
 */
@SpringBootApplication
@EnableJpaRepositories
public class PocMigrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(PocMigrationApplication.class, args);
    }
}