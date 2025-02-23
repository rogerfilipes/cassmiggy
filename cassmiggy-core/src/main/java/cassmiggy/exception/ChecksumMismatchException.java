package cassmiggy.exception;

/**
 * Exception thrown when an applied migration file has been modified.
 *
 */
public class ChecksumMismatchException extends MigrationException {

    private final String filename;
    private final String expected;
    private final String actual;

    public ChecksumMismatchException(String filename, String expected, String actual) {
        super(String.format(
                "Checksum mismatch for migration '%s'. Expected: %s, Found: %s. Migration file was modified after being applied.",
                filename, expected, actual));
        this.filename = filename;
        this.expected = expected;
        this.actual = actual;
    }


    public String getFilename() {
        return filename;
    }

    public String getExpected() {
        return expected;
    }

    public String getActual() {
        return actual;
    }
}
