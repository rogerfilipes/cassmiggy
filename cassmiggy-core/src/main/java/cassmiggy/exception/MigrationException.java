package cassmiggy.exception;

/**
 * Base exception for all migration-related errors.
 *
 * <p>This is an unchecked exception to simplify error handling.
 */
public class MigrationException extends RuntimeException {

    public MigrationException(String message) {
        super(message);
    }

    public MigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
