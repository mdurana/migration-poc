package com.poc.migration.util;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for parsing and formatting migration job IDs.
 */
@Slf4j
public class MigrationJobIdParser {
    
    private static final Pattern JOB_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    
    private MigrationJobIdParser() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Validates if a string is a valid job ID format.
     */
    public static boolean isValidJobId(String jobId) {
        if (jobId == null || jobId.isEmpty()) {
            return false;
        }
        return JOB_ID_PATTERN.matcher(jobId).matches();
    }
    
    /**
     * Validates job ID and throws exception if invalid.
     */
    public static void validateJobId(String jobId) {
        if (!isValidJobId(jobId)) {
            throw new IllegalArgumentException("Invalid job ID format: " + jobId);
        }
    }
    
    /**
     * Filters out invalid job IDs from a list.
     */
    public static List<String> filterValidJobIds(List<String> jobIds) {
        List<String> validIds = new ArrayList<>();
        
        for (String jobId : jobIds) {
            if (isValidJobId(jobId)) {
                validIds.add(jobId);
            } else {
                log.warn("Filtering out invalid job ID: {}", jobId);
            }
        }
        
        return validIds;
    }
    
    /**
     * Format job ID for display.
     */
    public static String formatJobId(String jobId) {
        if (jobId == null) {
            return "null";
        }
        
        // Truncate long IDs for display
        if (jobId.length() > 20) {
            return jobId.substring(0, 17) + "...";
        }
        
        return jobId;
    }
}


