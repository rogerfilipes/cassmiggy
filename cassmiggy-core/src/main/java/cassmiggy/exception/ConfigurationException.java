package cassmiggy.exception;

/**
 * Exception thrown when migration configuration is invalid.
 */
public class ConfigurationException extends MigrationException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
