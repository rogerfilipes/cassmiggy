package cassmiggy.exception;

/**
 * Thrown when SchemaMigrator operations are attempted before infrastructure
 * tables (history and lock) have been initialized.
 *
 * <p>This exception typically occurs when using custom SPI constructors without
 * calling {@code bootstrap()} first. The default Cassandra-backed constructors
 * bootstrap lazily during operation execution, but custom constructors do not.
 *
 * <h2>How to fix</h2>
 * <pre>{@code
     * // When using custom SPI constructors, call bootstrap() before migrate()
 * SchemaMigrator migrator = new SchemaMigrator(config, discovery, repository, lock);
 * migrator.bootstrap();  // Initialize infrastructure tables
 * migrator.migrate();    // Now safe to call
 * }</pre>
 *
 * @see cassmiggy.SchemaMigrator#bootstrap()
 */
public class InfrastructureNotInitializedException extends MigrationException {

    public InfrastructureNotInitializedException(String historyTableName, String lockTableName) {
        super(String.format(
                "Infrastructure tables '%s' and '%s' must exist. "
                        + "When using custom SPI constructors, you must call bootstrap() "
                        + "before performing migrations. Example: migrator.bootstrap(); migrator.migrate();",
                historyTableName, lockTableName));
    }
}
