package cassmiggy.spi;

import java.util.List;
import java.util.Optional;

import cassmiggy.model.MigrationRecord;

/** Stores and reads migration history records. */
public interface MigrationRepository {

    /** Applied migrations, most recent first. */
    List<MigrationRecord> getAppliedMigrations();

    Optional<MigrationRecord> getMigration(String path);

    void recordSuccess(
            String path, String filename, String description, String checksum, long executionMs, int statements);

    void recordFailure(
            String path,
            String filename,
            String description,
            String checksum,
            long executionMs,
            int statements,
            String errorMessage);
}
