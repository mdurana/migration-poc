package com.poc.migration.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST API endpoints.
 * Provides consistent error responses across the application.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * Handle validation exceptions from request validation.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex) {
        
        Map<String, Object> errors = new HashMap<>();
        errors.put("timestamp", LocalDateTime.now());
        errors.put("status", HttpStatus.BAD_REQUEST.value());
        errors.put("error", "Validation Failed");
        
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        errors.put("fieldErrors", fieldErrors);
        
        return ResponseEntity.badRequest().body(errors);
    }
    
    /**
     * Handle configuration exceptions.
     */
    @ExceptionHandler(ConfigurationException.class)
    public ResponseEntity<Map<String, Object>> handleConfigurationException(
            ConfigurationException ex) {
        
        log.error("Configuration error: {}", ex.getMessage(), ex);
        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Configuration Error",
            ex.getMessage()
        );
    }
    
    /**
     * Handle connection exceptions.
     */
    @ExceptionHandler(ConnectionException.class)
    public ResponseEntity<Map<String, Object>> handleConnectionException(
            ConnectionException ex) {
        
        log.error("Connection error: {}", ex.getMessage(), ex);
        return buildErrorResponse(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Database Connection Error",
            ex.getMessage()
        );
    }
    
    /**
     * Handle schema exceptions.
     */
    @ExceptionHandler(SchemaException.class)
    public ResponseEntity<Map<String, Object>> handleSchemaException(
            SchemaException ex) {
        
        log.error("Schema error: {}", ex.getMessage(), ex);
        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Schema Operation Failed",
            ex.getMessage()
        );
    }
    
    /**
     * Handle data migration exceptions.
     */
    @ExceptionHandler(DataMigrationException.class)
    public ResponseEntity<Map<String, Object>> handleDataMigrationException(
            DataMigrationException ex) {
        
        log.error("Data migration error: {}", ex.getMessage(), ex);
        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Data Migration Failed",
            ex.getMessage()
        );
    }
    
    /**
     * Handle validation exceptions.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            ValidationException ex) {
        
        log.error("Validation error: {}", ex.getMessage(), ex);
        return buildErrorResponse(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Validation Failed",
            ex.getMessage()
        );
    }
    
    /**
     * Handle generic migration exceptions.
     */
    @ExceptionHandler(MigrationException.class)
    public ResponseEntity<Map<String, Object>> handleMigrationException(
            MigrationException ex) {
        
        log.error("Migration error: {}", ex.getMessage(), ex);
        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Migration Error",
            ex.getMessage()
        );
    }
    
    /**
     * Handle all other uncaught exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex) {
        
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal Server Error",
            "An unexpected error occurred. Please check logs for details."
        );
    }
    
    /**
     * Build standardized error response.
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String error, String message) {
        
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        
        return ResponseEntity.status(status).body(body);
    }
}


