package cassmiggy;

import cassmiggy.model.MigrationResult;
import cassmiggy.model.MigrationStatus;

/**
 * Runs Cassandra schema migrations.
 *
 * <p>Call {@link #migrate()} to apply any pending {@code .cql} files, or {@link #getStatus()} to see
 * what's applied and pending without changing anything. The default implementation is
 * {@link SchemaMigrator}.
 */
public interface Migrator {

    /**
     * Executes all pending migrations.
     *
     * <p>This method performs the complete migration lifecycle:
     * <ol>
     *   <li>Initialize infrastructure tables (history, lock)</li>
     *   <li>Acquire lock</li>
     *   <li>Discover migration files</li>
     *   <li>Validate checksums of applied migrations</li>
     *   <li>Filter to determine pending migrations</li>
     *   <li>Execute each pending migration</li>
     *   <li>Record results in history table</li>
     *   <li>Release lock</li>
     * </ol>
     *
     * @return summary of the execution
     * @throws cassmiggy.exception.ChecksumMismatchException if an applied migration file was modified
     *
     * @throws cassmiggy.exception.LockAcquisitionException  if lock cannot be acquired within timeout
     *
     * @throws cassmiggy.exception.SchemaAgreementException if schema agreement times out after DDL
     *
     * @throws cassmiggy.exception.StatementExecutionException if a CQL statement fails to execute
     *
     */
    MigrationResult migrate();

    /**
     * Returns the current migration status without executing anything.
     *
     * <p>Reads the history table from the target cluster
     *
     * @return current status: applied, pending, and failed migrations
     */
    MigrationStatus getStatus();
}
