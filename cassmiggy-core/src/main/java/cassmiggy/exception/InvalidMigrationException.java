package cassmiggy.exception;

/**
 * Exception thrown when a migration file doesn't match the expected naming pattern
 * (V{version}__{description}.cql) or is otherwise invalid.
 */
public class InvalidMigrationException extends MigrationException {

    private final String filename;
    private final String reason;

    public InvalidMigrationException(String filename, String reason) {
        super(String.format("Invalid migration file '%s': %s", filename, reason));
        this.filename = filename;
        this.reason = reason;
    }

    public InvalidMigrationException(String filename, String reason, Throwable cause) {
        super(String.format("Invalid migration file '%s': %s", filename, reason), cause);
        this.filename = filename;
        this.reason = reason;
    }

    public String getFilename() {
        return filename;
    }

    public String getReason() {
        return reason;
    }
}
