package cassmiggy.model;

import java.util.List;

/**
 * Represents the current status of migrations without executing anything.
 * Use {@link Migrator#getStatus()} to obtain an instance.
 */
public final class MigrationStatus {
    private final List<MigrationRecord> applied;
    private final List<MigrationFile> pending;
    private final List<MigrationRecord> failed;

    public MigrationStatus(
            List<MigrationRecord> applied, List<MigrationFile> pending, List<MigrationRecord> failed) {
        this.applied = applied;
        this.pending = pending;
        this.failed = failed;
    }

    public List<MigrationRecord> getApplied() {
        return applied;
    }

    public List<MigrationFile> getPending() {
        return pending;
    }

    public List<MigrationRecord> getFailed() {
        return failed;
    }

    public boolean isUpToDate() {
        return pending.isEmpty() && failed.isEmpty();
    }

    public boolean hasFailures() {
        return !failed.isEmpty();
    }

    /**
     * @return the last applied migration filename (alphabetically), or null if none
     */
    public String lastApplied() {
        return applied.stream()
                .map(MigrationRecord::getFilename)
                .max(String::compareTo)
                .orElse(null);
    }
}
