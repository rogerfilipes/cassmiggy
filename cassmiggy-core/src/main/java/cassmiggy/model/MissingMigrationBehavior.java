package cassmiggy.model;

/**
 * Configures how the migrator handles a previously applied migration file
 * that no longer exists in the migration sources.
 *
 * <p>This situation can occur when:
 * <ul>
 *   <li>A migration file was accidentally deleted</li>
 *   <li>Git merge conflicts removed migration files</li>
 *   <li>Switching between branches with different migration histories</li>
 *   <li>Migration files were excluded from a build artifact</li>
 * </ul>
 *
 * @see Config.Builder#withMissingMigrationBehavior(MissingMigrationBehavior)
 */
public enum MissingMigrationBehavior {

    /**
     * Log a warning and continue validation.
     *
     * <p>Use this when migration file availability cannot be strictly guaranteed,
     * such as legacy systems or gradual migration adoption.
     */
    WARN,

    /**
     * Throw a {@link cassmiggy.exception.MissingMigrationException} and halt.
     *
     * <p>This is the default behavior, enforcing strict migration file integrity.
     * Any missing file indicates a potential problem (accidental deletion, merge
     * conflict, or deployment issue) that should block the migration.
     */
    FAIL,

    /**
     * Skip missing files without any logging.
     *
     * <p>Use this during local development when frequently switching between
     * branches with different migration histories. The missing files are
     * expected and not worth logging.
     */
    IGNORE
}
