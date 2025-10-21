package com.poc.migration.executor;

import com.poc.migration.model.JobRequest;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.FileSystemResourceAccessor;
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
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;

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
     * 2. Parses the generated XML changelog and auto-translates common types.
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
                    
                    // This is a simple POC translation. A real one would be more complex
                    // (e.g., BIGINT -> BIGSERIAL, SMALLINT -> SMALLSERIAL)
                    column.setAttribute("type", "SERIAL"); 
                    log.debug("Normalized autoIncrement to SERIAL for node: {}", column.getAttribute("name"));
                    // Continue to next column node, as SERIAL handles the type
                    continue; 
                }

                // 4. Handle Data Type Mappings
                if (column.hasAttribute("type") && typeMappings != null) {
                    String originalType = column.getAttribute("type").toUpperCase();
                    String mappingKey = sourceType + "." + originalType; // e.g., "mysql.TINYINT"
                    
                    if (typeMappings.containsKey(mappingKey)) {
                        String newType = typeMappings.get(mappingKey);
                        log.debug("Normalizing type: {} -> {}", mappingKey, newType);
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