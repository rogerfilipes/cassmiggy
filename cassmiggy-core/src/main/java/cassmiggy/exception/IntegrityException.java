package cassmiggy.exception;

import java.nio.file.Path;

/**
 * Exception thrown when integrity operations fail.
 * This includes errors reading files for checksum calculation
 * or algorithm unavailability.
 */
public class IntegrityException extends MigrationException {

    private final String filePath;

    public IntegrityException(String message) {
        super(message);
        this.filePath = null;
    }

    public IntegrityException(String message, Throwable cause) {
        super(message, cause);
        this.filePath = null;
    }

    public IntegrityException(Path filePath, String message, Throwable cause) {
        super(String.format("Integrity check failed for '%s': %s", filePath, message), cause);
        this.filePath = filePath.toString();
    }

    public String getFilePath() {
        return filePath;
    }
}
