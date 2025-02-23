package cassmiggy.exception;

import java.time.Duration;

/**
 * Exception thrown when schema agreement is not reached across the Cassandra cluster
 * within the configured timeout after executing a DDL statement.
 */
public class SchemaAgreementException extends MigrationException {

    private final String filename;
    private final int statementIndex;
    private final Duration timeout;

    public SchemaAgreementException(String filename, int statementIndex, Duration timeout) {
        super(String.format(
                "Schema agreement not reached for '%s' (statement %d) after %s. "
                        + "Check cluster health and node availability.",
                filename, statementIndex, formatDuration(timeout)));
        this.filename = filename;
        this.statementIndex = statementIndex;
        this.timeout = timeout;
    }

    public SchemaAgreementException(String filename, int statementIndex, Duration timeout, Throwable cause) {
        super(
                String.format(
                        "Schema agreement not reached for '%s' (statement %d) after %s. "
                                + "Check cluster health and node availability.",
                        filename, statementIndex, formatDuration(timeout)),
                cause);
        this.filename = filename;
        this.statementIndex = statementIndex;
        this.timeout = timeout;
    }

    public String getFilename() {
        return filename;
    }

    public int getStatementIndex() {
        return statementIndex;
    }

    public Duration getTimeout() {
        return timeout;
    }

    private static String formatDuration(Duration duration) {
        if (duration == null) {
            return "unknown";
        }
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + " seconds";
        }
        long minutes = seconds / 60;
        return minutes + " minutes";
    }
}
