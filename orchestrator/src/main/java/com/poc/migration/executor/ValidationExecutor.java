package com.poc.migration.executor;

import com.poc.migration.model.JobRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

@Service
@Slf4j
@RequiredArgsConstructor
public class ValidationExecutor {

    public boolean validateRowCounts(JobRequest request) {
        log.info("Starting row count validation...");
        boolean allValid = true;

        JobRequest.DbConfig source = request.getSource();
        JobRequest.DbConfig target = request.getTarget();

        String sourceJdbc = String.format("jdbc:%s://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                source.getType(), source.getHost(), source.getPort(), source.getDatabase());
        String targetJdbc = String.format("jdbc:%s://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                target.getType(), target.getHost(), target.getPort(), target.getDatabase());

        for (String table : request.getTablesToMigrate()) {
            try {
                long sourceCount = getCount(sourceJdbc, source.getUser(), source.getPassword(), table);
                long targetCount = getCount(targetJdbc, target.getUser(), target.getPassword(), table);
                
                if (sourceCount == targetCount) {
                    log.info("[VALIDATE] Table '{}': Source({}) == Target({}) -> PASS", table, sourceCount, targetCount);
                } else {
                    log.warn("[VALIDATE] Table '{}': Source({}) != Target({}) -> FAIL", table, sourceCount, targetCount);
                    allValid = false;
                }
            } catch (Exception e) {
                log.error("[VALIDATE] Failed to validate table '{}': {}", table, e.getMessage());
                allValid = false;
            }
        }
        return allValid;
    }

    private long getCount(String jdbcUrl, String user, String pass, String table) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + table;
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, pass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return -1; // Indicate failure
    }
}