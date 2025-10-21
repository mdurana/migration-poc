package com.poc.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableJpaRepositories
@EnableAsync // Enable @Async for the state machine
public class PocMigrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(PocMigrationApplication.class, args);
    }
}