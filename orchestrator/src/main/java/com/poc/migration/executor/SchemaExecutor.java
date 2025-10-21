package com.poc.migration.executor;

import com.poc.migration.model.JobRequest;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.serializer.ChangeLogSerializer;
import liquibase.serializer.core.xml.XMLChangeLogSerializer;
import liquibase.resource.DirectoryResourceAccessor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.File;

import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.snapshot.SnapshotControl;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.diff.DiffResult;
import liquibase.diff.DiffGeneratorFactory;
import liquibase.diff.compare.CompareControl;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.DiffToChangeLog;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import liquibase.exception.DatabaseException;

@Service
@Slf4j
public class SchemaExecutor {

    /**
     * Connects to the SOURCE database and generates a Liquibase changelog file.
     * This captures the current schema DDL.
     * * UPDATED to use the modern, programmatic diff-then-serialize approach.
     */
    public void generateChangelog(JobRequest request, String changelogPath) throws Exception {
        JobRequest.DbConfig source = request.getSource();
        String jdbcUrl = buildJdbcUrl(source);
        log.info("Generating Liquibase changelog from source: {}", jdbcUrl);

        Connection connection = null;
        Database database = null;
        Database referenceDatabase = null; // For the "empty" snapshot

        try (PrintStream outputStream = new PrintStream(new FileOutputStream(changelogPath))) {

            // 1. Get the connection and Liquibase Database object for the *source*
            connection = DriverManager.getConnection(jdbcUrl, source.getUser(), source.getPassword());
            database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));

            // 2. Create an "empty" offline database snapshot to compare against
            // We use the same URL just to get the correct database type, but it's "offline"
            referenceDatabase = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            SnapshotControl referenceSnapshotControl = new SnapshotControl(referenceDatabase);
            DatabaseSnapshot referenceSnapshot = SnapshotGeneratorFactory.getInstance().createSnapshot(referenceDatabase.getDefaultSchema(), referenceDatabase, referenceSnapshotControl);

            // 3. Create a snapshot of the *current* source database
            SnapshotControl targetSnapshotControl = new SnapshotControl(database);
            DatabaseSnapshot targetSnapshot = SnapshotGeneratorFactory.getInstance().createSnapshot(database.getDefaultSchema(), database, targetSnapshotControl);

            // 4. Compare the two (empty vs. current)
            CompareControl compareControl = new CompareControl();
            DiffResult diffResult = DiffGeneratorFactory.getInstance().compare(referenceSnapshot, targetSnapshot, compareControl);
            
            // 5. Create the serializer
            ChangeLogSerializer serializer = new XMLChangeLogSerializer();

            // 6. Create the DiffToChangeLog object *with the valid DiffResult*
            // This fixes the NullPointerException
            DiffToChangeLog diffToChangeLog = new DiffToChangeLog(diffResult, new DiffOutputControl());
            
            // 7. Serialize the changelog to the output stream
            diffToChangeLog.print(outputStream, serializer);

            log.info("Changelog generated successfully at {}", changelogPath);

        } catch (Exception e) {
            log.error("Failed to generate changelog: {}", e.getMessage(), e);
            throw e;
        } finally {
            // Ensure all database connections are closed
            try {
                if (referenceDatabase != null) {
                    referenceDatabase.close();
                }
            } catch (DatabaseException e) {
                log.warn("Error closing reference database: {}", e.getMessage());
            }

            try {
                if (database != null) {
                    database.close();
                }
            } catch (DatabaseException e) {
                log.warn("Error closing database: {}", e.getMessage());
            }

            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                log.warn("Error closing connection: {}", e.getMessage());
            }
        }
    }

    /**
     * Parses the generated XML changelog and auto-translates common types.
     * This is a "best-effort" translation for the POC.
     */
    public void normalizeChangelog(JobRequest request, String inputPath, String outputPath) throws Exception {
        Map<String, String> typeMappings = request.getDataTypeMappings();
        String sourceType = request.getSource().getType(); // e.g., "mysql"
        String targetType = request.getTarget().getType(); // e.g., "postgresql"

        // 1. Load the XML Document
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(inputPath));

        // 2. Find all <column> nodes
        NodeList columnNodes = doc.getElementsByTagName("column");
        for (int i = 0; i < columnNodes.getLength(); i++) {
            Node node = columnNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element column = (Element) node;
                
                // 3. Handle Auto-Increment
                // e.g., MySQL autoIncrement="true" -> PostgreSQL type="SERIAL"
                if ("postgresql".equals(targetType) && "true".equalsIgnoreCase(column.getAttribute("autoIncrement"))) {
                    column.removeAttribute("autoIncrement");
                    
                    // This is a simple POC translation.
                    column.setAttribute("type", "SERIAL"); 
                    log.debug("Normalized autoIncrement to SERIAL for node: {}", column.getAttribute("name"));
                    continue; 
                }

                // 4. Handle Data Type Mappings
                if (column.hasAttribute("type") && typeMappings != null) {
                    String originalType = column.getAttribute("type").toUpperCase();
                    // Handle types with parameters like VARCHAR(100)
                    String baseType = originalType.split("\\(")[0];
                    String mappingKey = sourceType + "." + baseType; // e.g., "mysql.TINYINT" or "mysql.VARCHAR"
                    
                    if (typeMappings.containsKey(mappingKey)) {
                        String newType = typeMappings.get(mappingKey);
                        log.debug("Normalizing type: {} -> {}", mappingKey, newType);
                        
                        // Preserve parameters if the new type needs them (e.g., VARCHAR(100) -> CHARACTER VARYING(100))
                        if (originalType.contains("(")) {
                            String params = originalType.substring(originalType.indexOf("("));
                            column.setAttribute("type", newType + params);
                        } else {
                            column.setAttribute("type", newType);
                        }
                    } else if (typeMappings.containsKey(sourceType + "." + originalType)) {
                         // Fallback for types without params like TINYINT
                         String newType = typeMappings.get(sourceType + "." + originalType);
                         log.debug("Normalizing type: {} -> {}", sourceType + "." + originalType, newType);
                         column.setAttribute("type", newType);
                    }
                }
            }
        }
        
        // 5. TODO: Add logic for other objects (e.g., find/replace in <sql> tags for functions)
        // For this POC, we are only handling <column> attributes.

        // 6. Write the modified XML to the new output file
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource domSource = new DOMSource(doc);
        StreamResult streamResult = new StreamResult(new File(outputPath));
        transformer.transform(domSource, streamResult);
        
        log.info("Normalization complete. Normalized changelog saved to {}", outputPath);
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
            DirectoryResourceAccessor resourceAccessor = new DirectoryResourceAccessor(new File("/"));

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
        // --- MAKE HELPER TYPE-AWARE ---
        if ("postgresql".equals(config.getType())) {
            return String.format("jdbc:postgresql://%s:%d/%s",
                config.getHost(), config.getPort(), config.getDatabase());
        }
        
        // Default to MySQL
        return String.format("jdbc:%s://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                config.getType(), config.getHost(), config.getPort(), config.getDatabase());
    }
}