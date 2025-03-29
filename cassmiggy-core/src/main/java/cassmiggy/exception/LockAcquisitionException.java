package cassmiggy.exception;

import java.time.Duration;

/**
 * Exception thrown when the migration lock cannot be acquired within the configured timeout.
 */
public class LockAcquisitionException extends MigrationException {

    private static final String UNKNOWN = "unknown";
    private static final String MESSAGE_FORMAT = "Failed to acquire migration lock after %s. Current owner: %s";

    private final Duration timeout;
    private final String currentOwner;

    public LockAcquisitionException(Duration timeout, String currentOwner) {
        super(buildMessage(timeout, currentOwner));
        this.timeout = timeout;
        this.currentOwner = currentOwner;
    }

    public LockAcquisitionException(Duration timeout, String currentOwner, Throwable cause) {
        super(buildMessage(timeout, currentOwner), cause);
        this.timeout = timeout;
        this.currentOwner = currentOwner;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public String getCurrentOwner() {
        return currentOwner;
    }

    private static String buildMessage(Duration timeout, String currentOwner) {
        return String.format(MESSAGE_FORMAT, formatDuration(timeout), currentOwner != null ? currentOwner : UNKNOWN);
    }

    private static String formatDuration(Duration duration) {
        if (duration == null) {
            return UNKNOWN;
        }
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + " seconds";
        }
        long minutes = seconds / 60;
        return minutes + " minutes";
    }
}
