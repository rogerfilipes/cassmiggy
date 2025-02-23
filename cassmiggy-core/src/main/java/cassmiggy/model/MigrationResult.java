package cassmiggy.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a migration execution containing timing and applied migration details.
 *
 * @see Migrator#migrate()
 */
public final class MigrationResult {

    private final Instant startedAt;
    private final Instant completedAt;
    private final int appliedCount;
    private final int failedCount;
    private final List<AppliedMigration> appliedMigrations;

    private MigrationResult(Builder builder) {
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.appliedCount = builder.appliedMigrations.size();
        this.failedCount = builder.failedCount;
        this.appliedMigrations = Collections.unmodifiableList(new ArrayList<>(builder.appliedMigrations));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    /**
     * @return the total duration of the migration execution, or {@link Duration#ZERO} if incomplete
     */
    public Duration getDuration() {
        if (startedAt == null || completedAt == null) {
            return Duration.ZERO;
        }
        return Duration.between(startedAt, completedAt);
    }

    public int getAppliedCount() {
        return appliedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    /**
     * @return an unmodifiable list of applied migrations with execution details
     */
    public List<AppliedMigration> getAppliedMigrations() {
        return appliedMigrations;
    }

    public boolean isSuccess() {
        return failedCount == 0;
    }

    @Override
    public String toString() {
        return String.format(
                "MigrationResult{applied=%d, failed=%d, duration=%dms}",
                appliedCount, failedCount, getDuration().toMillis());
    }

    /**
     * Details of a single applied migration.
     */
    public static final class AppliedMigration {

        private final String path;
        private final String description;
        private final int statementCount;
        private final Duration executionTime;
        private final boolean success;

        public AppliedMigration(
                String path, String description, int statementCount, Duration executionTime, boolean success) {
            this.path = path;
            this.description = description;
            this.statementCount = statementCount;
            this.executionTime = executionTime;
            this.success = success;
        }

        public String getPath() {
            return path;
        }

        public String getDescription() {
            return description;
        }

        public int getStatementCount() {
            return statementCount;
        }

        public Duration getExecutionTime() {
            return executionTime;
        }

        public boolean isSuccess() {
            return success;
        }
    }

    /**
     * Builder for constructing {@link MigrationResult} instances.
     */
    public static class Builder {
        private Instant startedAt;
        private Instant completedAt;
        private int failedCount = 0;
        private final List<AppliedMigration> appliedMigrations = new ArrayList<>();

        private Builder() {}

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        /**
         * Adds a successfully applied migration to the result.
         *
         * @param path the migration path (relative path from migrations root)
         */
        public Builder addApplied(String path, String description, int statementCount, Duration executionTime) {
            appliedMigrations.add(new AppliedMigration(path, description, statementCount, executionTime, true));
            return this;
        }

        public Builder incrementFailed() {
            this.failedCount++;
            return this;
        }

        public MigrationResult build() {
            return new MigrationResult(this);
        }
    }
}
