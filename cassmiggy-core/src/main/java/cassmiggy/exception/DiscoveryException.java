package cassmiggy.exception;

/**
 * Exception thrown when migration file discovery fails.
 * This includes errors scanning directories, reading classpath resources,
 * or accessing migration files.
 */
public class DiscoveryException extends MigrationException {

    private final String location;

    public DiscoveryException(String message) {
        super(message);
        this.location = null;
    }

    public DiscoveryException(String message, Throwable cause) {
        super(message, cause);
        this.location = null;
    }

    public DiscoveryException(String location, String message, Throwable cause) {
        super(String.format("Failed to discover migrations at '%s': %s", location, message), cause);
        this.location = location;
    }

    public String getLocation() {
        return location;
    }
}
