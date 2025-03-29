package cassmiggy.exception;

/**
 * Exception thrown when the migration lock is lost during execution.
 *
 * <p>This typically occurs when the heartbeat thread fails to update
 * the lock and another instance takes over. When this exception is
 * thrown, the migration process stops immediately.
 *
 * <p>The migration that was about to execute has NOT started. Any
 * previously completed migrations remain committed.
 */
public class LockLostException extends MigrationException {

    private final String pendingMigration;

    public LockLostException(String pendingMigration) {
        super(buildMessage(pendingMigration));
        this.pendingMigration = pendingMigration;
    }

    public LockLostException(String pendingMigration, Throwable cause) {
        super(buildMessage(pendingMigration), cause);
        this.pendingMigration = pendingMigration;
    }

    public String getPendingMigration() {
        return pendingMigration;
    }

    private static String buildMessage(String migration) {
        if (migration == null || migration.isBlank()) {
            return "Lock lost during migration execution. Another instance may have taken over.";
        }
        return String.format(
                "Lock lost before executing migration '%s'. Another instance may have taken over. "
                        + "Check migration history for current state.",
                migration);
    }
}
