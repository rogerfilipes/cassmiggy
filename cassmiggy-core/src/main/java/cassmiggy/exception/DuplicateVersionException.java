package cassmiggy.exception;

/**
 * Exception thrown when duplicate migration files are detected.
 * This typically occurs with case-insensitive filename conflicts.
 */
public class DuplicateVersionException extends MigrationException {

    private final String filename;

    public DuplicateVersionException(String filename, String reason) {
        super(String.format("Duplicate migration detected: '%s'. %s", filename, reason));
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }
}
