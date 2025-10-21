package com.poc.migration.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.time.Duration;

/**
 * Represents a migration job in the system.
 * Tracks the lifecycle and status of database migrations.
 */
@Entity
@Table(name = "migration_jobs", indexes = {
    @Index(name = "idx_job_name", columnList = "jobName"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User-friendly name for the job.
     * Not unique - users may run the same migration multiple times.
     */
    @Column(nullable = false, length = 255)
    private String jobName;

    /**
     * Current status of the job.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobStatus status;

    /**
     * Original request as JSON for audit/replay purposes.
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String jobRequestJson;

    /**
     * Last error message if job failed.
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String lastError;

    /**
     * Timestamp when job was created.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when job was last updated.
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Timestamp when job reached terminal state (success or failure).
     */
    private LocalDateTime completedAt;

    /**
     * Total execution time in milliseconds (if completed).
     */
    private Long executionTimeMs;

    /**
     * Number of tables successfully migrated.
     */
    @Builder.Default
    private Integer tablesCompleted = 0;

    /**
     * Total number of tables to migrate.
     */
    @Builder.Default
    private Integer tablesTotal = 0;

    /**
     * Progress percentage (0-100).
     */
    @Transient
    public Integer getProgressPercentage() {
        if (tablesTotal == null || tablesTotal == 0) {
            return 0;
        }
        return (int) ((tablesCompleted * 100.0) / tablesTotal);
    }

    /**
     * Get execution time as Duration.
     */
    @Transient
    public Duration getExecutionDuration() {
        if (executionTimeMs != null) {
            return Duration.ofMillis(executionTimeMs);
        }
        if (completedAt != null && createdAt != null) {
            return Duration.between(createdAt, completedAt);
        }
        if (createdAt != null) {
            return Duration.between(createdAt, LocalDateTime.now());
        }
        return Duration.ZERO;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = JobStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        
        // If status changed to terminal, set completedAt and calculate duration
        if (status != null && status.isTerminal() && completedAt == null) {
            completedAt = updatedAt;
            if (createdAt != null) {
                executionTimeMs = Duration.between(createdAt, completedAt).toMillis();
            }
        }
    }

    /**
     * Mark job as completed (success or failure).
     */
    public void complete(JobStatus terminalStatus) {
        if (!terminalStatus.isTerminal()) {
            throw new IllegalArgumentException("Status must be terminal: " + terminalStatus);
        }
        this.status = terminalStatus;
        this.completedAt = LocalDateTime.now();
        if (this.createdAt != null) {
            this.executionTimeMs = Duration.between(this.createdAt, this.completedAt).toMillis();
        }
    }

    /**
     * Update progress.
     */
    public void updateProgress(int completed, int total) {
        this.tablesCompleted = completed;
        this.tablesTotal = total;
    }
}