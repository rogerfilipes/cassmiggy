package cassmiggy.internal.validation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cassmiggy.model.MigrationFile;
import cassmiggy.model.MigrationRecord;
import cassmiggy.model.MissingMigrationBehavior;
import cassmiggy.exception.ChecksumMismatchException;
import cassmiggy.exception.MissingMigrationException;

/** Verifies applied migrations still match their files, by checksum. */
public class MigrationValidator {

    private static final Logger log = LoggerFactory.getLogger(MigrationValidator.class);

    private final MissingMigrationBehavior missingMigrationBehavior;

    public MigrationValidator() {
        this(MissingMigrationBehavior.FAIL);
    }

    public MigrationValidator(MissingMigrationBehavior missingMigrationBehavior) {
        this.missingMigrationBehavior =
                missingMigrationBehavior != null ? missingMigrationBehavior : MissingMigrationBehavior.FAIL;
    }

    public void validateChecksums(List<MigrationFile> discovered, List<MigrationRecord> applied) {
        log.debug("Validating checksums for {} applied migrations", applied.size());

        Map<String, MigrationFile> discoveredMap = new HashMap<>();
        for (MigrationFile m : discovered) {
            discoveredMap.put(m.getPath(), m);
        }

        for (MigrationRecord appliedMigration : applied) {
            if (appliedMigration.isFailed()) {
                log.trace("Skipping checksum validation for failed migration {}", appliedMigration.getPath());
                continue;
            }

            MigrationFile discoveredMigration = discoveredMap.get(appliedMigration.getPath());
            if (discoveredMigration == null) {
                handleMissingMigration(appliedMigration.getPath());
                continue;
            }

            if (!appliedMigration.getChecksum().equals(discoveredMigration.getChecksum())) {
                throw new ChecksumMismatchException(
                        appliedMigration.getPath(), appliedMigration.getChecksum(), discoveredMigration.getChecksum());
            }

            log.trace("Validated {} - checksum OK", appliedMigration.getPath());
        }

        log.trace("All checksums validated successfully");
    }

    private void handleMissingMigration(String migrationPath) {
        switch (missingMigrationBehavior) {
            case FAIL -> throw new MissingMigrationException(migrationPath);
            case WARN -> log.warn(
                    "Applied migration '{}' not found in sources. "
                            + "This may indicate accidental deletion or branch mismatch. "
                            + "Configure withMissingMigrationBehavior(FAIL) to enforce strict checking.",
                    migrationPath);
            case IGNORE -> log.trace("Ignoring missing migration: {}", migrationPath);
            default -> throw new IllegalStateException("Unexpected MissingMigrationBehavior: " + missingMigrationBehavior);
        }
    }
}
