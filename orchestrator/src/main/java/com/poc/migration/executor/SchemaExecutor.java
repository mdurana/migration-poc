package com.poc.migration.executor;

import com.poc.migration.model.JobRequest;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.FileSystemResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;

@Service
@Slf4j
public class SchemaExecutor {

    /**
     * Connects to the SOURCE database and generates a Liquibase changelog file.
     * This captures the current schema DDL.
     */
    public void generateChangelog(JobRequest request, String changelogPath) throws Exception {
        JobRequest.DbConfig source = request.getSource();
        String jdbcUrl = buildJdbcUrl(source);
        log.info("Generating Liquibase changelog from source: {}", jdbcUrl);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, source.getUser(), source.getPassword());
             PrintStream outputStream = new PrintStream(new FileOutputStream(changelogPath))) {
            
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            
            // Liquibase facade for generating changelog
            liquibase.command.CommandScope commandScope = new liquibase.command.CommandScope("generate-changelog");
            commandScope.addArgumentValue("database", database);
            
            // We tell Liquibase to output to our file stream
            commandScope.setOutput(outputStream);
            commandScope.execute();

            log.info("Changelog generated successfully at {}", changelogPath);

        } catch (Exception e) {
            log.error("Failed to generate changelog: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Connects to the TARGET database and applies the specified changelog file.
     * This creates the schema on the target.
     */
    public void applyChangelog(JobRequest request, String changelogPath) throws Exception {
        JobRequest.DbConfig target = request.getTarget();
        String jdbcUrl = buildJdbcUrl(target);
        log.info("Applying Liquibase changelog to target: {}", jdbcUrl);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, target.getUser(), target.getPassword())) {
            
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            
            // We use FileSystemResourceAccessor because the file is on the container's filesystem
            // We point it to the root "/" so it can find the absolute changelogPath
            FileSystemResourceAccessor resourceAccessor = new FileSystemResourceAccessor("/");

            // changelogPath is an absolute path like /app/generated-schema/job-1.xml
            Liquibase liquibase = new Liquibase(changelogPath, resourceAccessor, database);

            log.info("Starting Liquibase update on target...");
            liquibase.update(new liquibase.Contexts()); // Pass empty contexts
            
            log.info("Liquibase update on target finished successfully.");

        } catch (Exception e) {
            log.error("Failed to apply changelog to target: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Helper to build the JDBC URL.
     * For the POC, we disable SSL. This MUST be changed for production.
     */
    private String buildJdbcUrl(JobRequest.DbConfig config) {
        return String.format("jdbc:%s://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                config.getType(), config.getHost(), config.getPort(), config.getDatabase());
    }
}