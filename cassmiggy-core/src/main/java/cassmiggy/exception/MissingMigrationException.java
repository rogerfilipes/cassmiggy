package cassmiggy.exception;

/**
 * Thrown when a previously applied migration file cannot be found in the sources.
 *
 * <p>This exception indicates a mismatch between the migration history stored in
 * Cassandra and the migration files available to the application.
 * Most likely files were deleted after being apllied to the database
 *
 * <p>This exception is only thrown when
 * {@link cassmiggy.MissingMigrationBehavior#FAIL} is configured via
 * {@link cassmiggy.Config.Builder#withMissingMigrationBehavior}.
 *
 * @see cassmiggy.MissingMigrationBehavior
 */
public class MissingMigrationException extends MigrationException {

    private final String migrationPath;

    public MissingMigrationException(String migrationPath) {
        super(String.format(
                "Applied migration '%s' not found in sources. "
                        + "This may indicate accidental deletion, merge conflict, or deployment issue. "
                        + "To ignore missing files, configure withMissingMigrationBehavior(WARN)"
                        + " or withMissingMigrationBehavior(IGNORE).",
                migrationPath));
        this.migrationPath = migrationPath;
    }

    public String getMigrationPath() {
        return migrationPath;
    }
}
