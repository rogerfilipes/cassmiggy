package cassmiggy.internal.engine;

import cassmiggy.exception.MigrationException;

/** Carries statement progress for failure history while preserving the original public exception. */
public class MigrationExecutionException extends MigrationException {

    private final String filename;
    private final int executedStatements;
    private final MigrationException migrationException;

    MigrationExecutionException(String filename, int executedStatements, MigrationException migrationException) {
        super(migrationException.getMessage(), migrationException);
        this.filename = filename;
        this.executedStatements = executedStatements;
        this.migrationException = migrationException;
    }

    public String getFilename() {
        return filename;
    }

    public int getExecutedStatements() {
        return executedStatements;
    }

    public MigrationException getMigrationException() {
        return migrationException;
    }
}
