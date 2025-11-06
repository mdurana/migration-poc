package com.poc.migration.util;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * Utility class for retry logic with exponential backoff.
 */
@Slf4j
public class RetryUtil {
    
    private RetryUtil() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Execute operation with retry logic.
     * 
     * @param operation The operation to execute
     * @param maxAttempts Maximum number of attempts
     * @param delayMs Initial delay between attempts in milliseconds
     * @param operationName Name of the operation for logging
     * @return Result of the operation
     * @throws Exception if all attempts fail
     */
    public static <T> T executeWithRetry(
            Supplier<T> operation,
            int maxAttempts,
            long delayMs,
            String operationName) throws Exception {
        
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.debug("Attempting {}: attempt {}/{}", operationName, attempt, maxAttempts);
                return operation.get();
                
            } catch (Exception e) {
                lastException = e;
                
                if (attempt < maxAttempts) {
                    long currentDelay = delayMs * attempt; // Linear backoff
                    log.warn("Attempt {}/{} failed for {}: {}. Retrying in {}ms...",
                            attempt, maxAttempts, operationName, e.getMessage(), currentDelay);
                    
                    try {
                        Thread.sleep(currentDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Retry interrupted", ie);
                    }
                } else {
                    log.error("All {} attempts failed for {}", maxAttempts, operationName);
                }
            }
        }
        
        throw new Exception(
            String.format("Operation '%s' failed after %d attempts", operationName, maxAttempts),
            lastException
        );
    }
    
    /**
     * Execute operation with retry logic (void return).
     */
    public static void executeWithRetryVoid(
            Runnable operation,
            int maxAttempts,
            long delayMs,
            String operationName) throws Exception {
        
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, maxAttempts, delayMs, operationName);
    }
}


