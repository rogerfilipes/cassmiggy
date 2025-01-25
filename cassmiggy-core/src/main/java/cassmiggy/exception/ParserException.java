package cassmiggy.exception;

import java.nio.file.Path;

/**
 * Exception thrown when CQL parsing fails.
 * This includes errors reading migration files or parsing CQL content.
 */
public class ParserException extends MigrationException {

    private final String filePath;

    public ParserException(String message) {
        super(message);
        this.filePath = null;
    }

    public ParserException(String message, Throwable cause) {
        super(message, cause);
        this.filePath = null;
    }

    public ParserException(Path filePath, String message, Throwable cause) {
        super(String.format("Failed to parse CQL file '%s': %s", filePath, message), cause);
        this.filePath = filePath.toString();
    }

    public String getFilePath() {
        return filePath;
    }
}
