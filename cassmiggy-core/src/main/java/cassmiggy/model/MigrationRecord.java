package cassmiggy.model;

import java.time.Instant;

/**
 * Record of an applied migration from the schema history table.
 */
public final class MigrationRecord {
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private final String path;
    private final String filename;
    private final String description;
    private final String checksum;
    private final Instant executedAt;
    private final long executionMs;
    private final String status;
    private final String errorMessage;
    private final int statements;

    public MigrationRecord(
            String path,
            String filename,
            String description,
            String checksum,
            Instant executedAt,
            long executionMs,
            String status,
            String errorMessage,
            int statements) {
        this.path = path;
        this.filename = filename;
        this.description = description;
        this.checksum = checksum;
        this.executedAt = executedAt;
        this.executionMs = executionMs;
        this.status = status;
        this.errorMessage = errorMessage;
        this.statements = statements;
    }

    public String getPath() {
        return path;
    }

    public String getFilename() {
        return filename;
    }

    public String getDescription() {
        return description;
    }

    public String getChecksum() {
        return checksum;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public long getExecutionMs() {
        return executionMs;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getStatements() {
        return statements;
    }

    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }

    public boolean isFailed() {
        return STATUS_FAILED.equals(status);
    }
}
