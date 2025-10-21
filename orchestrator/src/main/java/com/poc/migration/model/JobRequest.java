package com.poc.migration.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.List;

// This DTO maps to the job-template.json
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobRequest {
    private String jobName;
    private DbConfig source;
    private DbConfig target;
    private List<String> tablesToMigrate;
    // 'liquibaseChangelogFile' was removed

    @Data
    public static class DbConfig {
        private String type;
        private String host;
        private int port;
        private String database;
        private String user;
        private String password;
    }
}