package com.poc.migration.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.poc.migration.model.JobRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataExecutor {

    // Inject config from application-poc.yaml
    @Value("${shardingsphere.admin.host}")
    private String adminHost;
    @Value("${shardingsphere.admin.port}")
    private int adminPort;
    @Value("${shardingsphere.admin.user}")
    private String adminUser;
    @Value("${shardingsphere.admin.password}")
    private String adminPassword;

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    // We use the ShardingSphere JDBC driver to send DistSQL
    private Connection getAdminConnection() throws Exception {
        String jdbcUrl = String.format("jdbc:shardingsphere:http://%s:%d/", adminHost, adminPort);
        // We must load the driver class
        Class.forName("org.apache.shardingsphere.driver.ShardingSphereDriver");
        return DriverManager.getConnection(jdbcUrl, adminUser, adminPassword);
    }

    public void registerSourceAndTarget(JobRequest request) throws Exception {
        // 1. Register Source
        String sourceRql = loadRql("register_source.rql");
        String sourceDef = buildDbDefinition("source_db", request.getSource());
        executeDistSql(sourceRql.replace("{{db_definition}}", sourceDef));

        // 2. Register Target
        String targetRql = loadRql("register_target.rql");
        String targetDef = buildDbDefinition("target_db", request.getTarget());
        executeDistSql(targetRql.replace("{{db_definition}}", targetDef));
    }

    public void createMigrationJob(Long jobId, JobRequest request) throws Exception {
        String rql = loadRql("create_job.rql");
        String jobConfig = buildJobConfig(request.getTablesToMigrate());
        
        String finalRql = rql
                .replace("{{job_name}}", "job_" + jobId)
                .replace("{{job_config}}", jobConfig);
        
        executeDistSql(finalRql);
    }
    
    public void startMigrationJob(Long jobId) throws Exception {
        String rql = loadRql("start_job.rql").replace("{{job_name}}", "job_" + jobId);
        executeDistSql(rql);
    }

    public void stopMigrationJob(Long jobId) throws Exception {
        String rql = "STOP MIGRATION JOB job_" + jobId;
        executeDistSql(rql);
    }

    public void monitorJobUntilCdc(Long jobId) throws Exception {
        String rql = loadRql("check_status.rql").replace("{{job_name}}", "job_" + jobId);
        boolean inInventory = true;

        while (true) {
            Thread.sleep(5000); // Poll every 5 seconds
            
            try (Connection conn = getAdminConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(rql)) {

                if (!rs.next()) {
                    throw new RuntimeException("Job status not found for job_" + jobId);
                }

                String status = rs.getString("status");
                String inventoryFinishedPercentage = rs.getString("inventory_finished_percentage");
                String incrementalDelayMilliseconds = rs.getString("incremental_delay_milliseconds");

                log.info("[Job-{}] Status: {}, Inventory: {}%, Lag (ms): {}", 
                    jobId, status, inventoryFinishedPercentage, incrementalDelayMilliseconds);

                if ("INVENTORY_FINISHED".equals(status)) {
                    inInventory = false;
                }

                if (!inInventory && "ALMOST_FINISHED".equals(status)) {
                    // "ALMOST_FINISHED" means CDC is running and lag is minimal
                    log.info("[Job-{}] CDC is in sync. Proceeding.", jobId);
                    return; // Success!
                }

                if (status.contains("ERROR") || status.contains("FAILED")) {
                    throw new RuntimeException("Migration job failed with status: " + status);
                }
            }
        }
    }


    // --- HELPER METHODS ---

    private void executeDistSql(String rql) throws Exception {
        log.info("Executing DistSQL: {}...", rql.substring(0, Math.min(rql.length(), 100)));
        try (Connection conn = getAdminConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(rql);
        }
    }

    private String buildDbDefinition(String id, JobRequest.DbConfig config) throws JsonProcessingException {
        // This converts the DbConfig into the JSON string ShardingSphere expects
        Map<String, Object> props = Map.of(
            "jdbcUrl", String.format("jdbc:%s://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                config.getType(), config.getHost(), config.getPort(), config.getDatabase()),
            "username", config.getUser(),
            "password", config.getPassword()
        );
        ObjectWriter writer = objectMapper.writer();
        return String.format("ID=%s, TYPE=Standard, PROPS(%s)", id, writer.writeValueAsString(props));
    }

    private String buildJobConfig(List<String> tables) throws JsonProcessingException {
        // Builds the JSON config for the CREATE JOB command
        Map<String, Object> config = Map.of(
            "source", Map.of(
                "type", "DATABASE", 
                "database", "source_db"
            ),
            "target", Map.of(
                "type", "DATABASE",
                "database", "target_db"
            ),
            "tables", tables
        );
        return objectMapper.writer().writeValueAsString(config);
    }
    
    private String loadRql(String fileName) throws Exception {
        Resource resource = resourceLoader.getResource("file:/app/data/" + fileName);
        try (InputStream is = resource.getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        }
    }
}