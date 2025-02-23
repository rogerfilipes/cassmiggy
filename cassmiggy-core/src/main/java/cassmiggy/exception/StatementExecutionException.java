package cassmiggy.exception;

/**
 * Exception thrown when a CQL statement execution fails during migration.
 */
public class StatementExecutionException extends MigrationException {

    private final String filename;
    private final int statementIndex;
    private final String statement;

    public StatementExecutionException(String filename, int statementIndex, String statement, Throwable cause) {
        super(
                String.format(
                        "Failed to execute statement %d in migration '%s': %s",
                        statementIndex, filename, truncateStatement(statement)),
                cause);
        this.filename = filename;
        this.statementIndex = statementIndex;
        this.statement = statement;
    }

    public StatementExecutionException(String filename, int statementIndex, String statement, String errorMessage) {
        super(String.format(
                "Failed to execute statement %d in migration '%s': %s. Error: %s",
                statementIndex, filename, truncateStatement(statement), errorMessage));
        this.filename = filename;
        this.statementIndex = statementIndex;
        this.statement = statement;
    }

    public StatementExecutionException(
            String filename, int statementIndex, String statement, String errorMessage, Throwable cause) {
        super(
                String.format(
                        "Failed to execute statement %d in migration '%s': %s. Error: %s",
                        statementIndex, filename, truncateStatement(statement), errorMessage),
                cause);
        this.filename = filename;
        this.statementIndex = statementIndex;
        this.statement = statement;
    }

    public String getFilename() {
        return filename;
    }

    public int getStatementIndex() {
        return statementIndex;
    }

    public String getStatement() {
        return statement;
    }

    private static String truncateStatement(String statement) {
        if (statement == null) {
            return "null";
        }
        String trimmed = statement.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > 100) {
            return trimmed.substring(0, 100) + "...";
        }
        return trimmed;
    }
}
