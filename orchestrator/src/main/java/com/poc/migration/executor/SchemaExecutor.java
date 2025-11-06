package com.poc.migration.executor;

import com.poc.migration.model.JobRequest;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
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
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;

@Service
@Slf4j
public class SchemaExecutor {

    /**
     * Generates changelog using Liquibase's command-line equivalent approach.
     * Most reliable method for full schema capture.
     */
    public void generateChangelog(JobRequest request, String changelogPath) throws Exception {
        JobRequest.DbConfig source = request.getSource();
        String jdbcUrl = buildJdbcUrl(source);
        log.info("Generating Liquibase changelog from source: {}", jdbcUrl);
    
        // Ensure output directory exists
        File outputFile = new File(changelogPath);
        outputFile.getParentFile().mkdirs();
    
        try {
            // Build Liquibase command programmatically
            java.util.Map<String, Object> scopeValues = new java.util.HashMap<>();
            scopeValues.put("liquibase.command.url", jdbcUrl);
            scopeValues.put("liquibase.command.username", source.getUser());
            scopeValues.put("liquibase.command.password", source.getPassword());
            scopeValues.put("liquibase.command.changelogFile", changelogPath);
    
            liquibase.Scope.child(scopeValues, () -> {
                liquibase.command.CommandScope commandScope = new liquibase.command.CommandScope("generateChangelog");
                commandScope.addArgumentValue("changelogFile", changelogPath);
                commandScope.execute();
            });
    
            log.info("Changelog generated successfully at {}", changelogPath);
    
        } catch (Exception e) {
            log.error("Failed to generate changelog: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Parses the generated XML changelog and auto-translates common types.
     * This is a "best-effort" translation for the POC.
     */
    public void normalizeChangelog(JobRequest request, String inputPath, String outputPath) throws Exception {
        Map<String, String> typeMappings = request.getDataTypeMappings();
        String sourceType = request.getSource().getType();
        String targetType = request.getTarget().getType();

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
                if ("postgresql".equals(targetType) && "true".equalsIgnoreCase(column.getAttribute("autoIncrement"))) {
                    column.removeAttribute("autoIncrement");
                    column.setAttribute("type", "SERIAL");
                    log.debug("Normalized autoIncrement to SERIAL for column: {}", column.getAttribute("name"));
                    continue;
                }

                // 4. Handle Data Type Mappings
                if (column.hasAttribute("type") && typeMappings != null) {
                    String originalType = column.getAttribute("type").toUpperCase();
                    String baseType = originalType.split("\\(")[0];
                    String mappingKey = sourceType + "." + baseType;
                    
                    if (typeMappings.containsKey(mappingKey)) {
                        String newType = typeMappings.get(mappingKey);
                        log.debug("Normalizing type: {} -> {}", mappingKey, newType);
                        
                        // Preserve parameters if present
                        if (originalType.contains("(")) {
                            String params = originalType.substring(originalType.indexOf("("));
                            column.setAttribute("type", newType + params);
                        } else {
                            column.setAttribute("type", newType);
                        }
                    } else if (typeMappings.containsKey(sourceType + "." + originalType)) {
                        String newType = typeMappings.get(sourceType + "." + originalType);
                        log.debug("Normalizing type: {} -> {}", sourceType + "." + originalType, newType);
                        column.setAttribute("type", newType);
                    }
                }
            }
        }

        // 5. Write the modified XML to output file
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
            
            DirectoryResourceAccessor resourceAccessor = new DirectoryResourceAccessor(Paths.get("/"));

            try (Liquibase liquibase = new Liquibase(changelogPath, resourceAccessor, database)) {
                log.info("Starting Liquibase update on target...");
                liquibase.update(new liquibase.Contexts());
                
                log.info("Liquibase update on target finished successfully.");
            }

        } catch (Exception e) {
            log.error("Failed to apply changelog to target: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Helper to build the JDBC URL with proper settings for each database type.
     */
    private String buildJdbcUrl(JobRequest.DbConfig config) {
        if ("postgresql".equals(config.getType())) {
            return String.format("jdbc:postgresql://%s:%d/%s?useSSL=false",
                config.getHost(), config.getPort(), config.getDatabase());
        }
        
        // Default to MySQL
        return String.format("jdbc:%s://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                config.getType(), config.getHost(), config.getPort(), config.getDatabase());
    }
}