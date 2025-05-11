package cassmiggy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import cassmiggy.internal.planning.MigrationPlanner;
import cassmiggy.model.MigrationFile;
import cassmiggy.model.MigrationRecord;

class MigrationPlannerTest {

    private final MigrationPlanner planner = new MigrationPlanner();

    @Test
    void neverAppliedFileIsPending() {
        List<MigrationFile> discovered = List.of(file("0001-create.cql"));

        List<MigrationFile> pending = planner.filterPending(discovered, List.of());

        assertThat(pending).extracting(MigrationFile::getPath).containsExactly("0001-create.cql");
    }

    @Test
    void successfullyAppliedFileIsNotPending() {
        List<MigrationFile> discovered = List.of(file("0001-create.cql"));
        List<MigrationRecord> applied = List.of(applied("0001-create.cql", MigrationRecord.STATUS_SUCCESS));

        assertThat(planner.filterPending(discovered, applied)).isEmpty();
    }

    @Test
    void failedFileIsPendingForRetry() {
        List<MigrationFile> discovered = List.of(file("0001-create.cql"));
        List<MigrationRecord> applied = List.of(applied("0001-create.cql", MigrationRecord.STATUS_FAILED));

        assertThat(planner.filterPending(discovered, applied))
                .extracting(MigrationFile::getPath)
                .containsExactly("0001-create.cql");
    }

    @Test
    void returnsOnlyPendingAndPreservesDiscoveryOrder() {
        List<MigrationFile> discovered = List.of(
                file("0001-done.cql"), file("0002-failed.cql"), file("0003-new.cql"));
        List<MigrationRecord> applied = List.of(
                applied("0001-done.cql", MigrationRecord.STATUS_SUCCESS),
                applied("0002-failed.cql", MigrationRecord.STATUS_FAILED));

        List<MigrationFile> pending = planner.filterPending(discovered, applied);

        assertThat(pending)
                .extracting(MigrationFile::getPath)
                .containsExactly("0002-failed.cql", "0003-new.cql");
    }

    @Test
    void appliedRecordWithNoDiscoveredFileIsIgnored() {
        List<MigrationFile> discovered = List.of(file("0001-create.cql"));
        List<MigrationRecord> applied = List.of(
                applied("0001-create.cql", MigrationRecord.STATUS_SUCCESS),
                applied("0000-deleted.cql", MigrationRecord.STATUS_SUCCESS));

        // The planner only reports work from discovered files; an orphaned history
        // record (missing migration) is not its concern and must not appear or fail.
        assertThat(planner.filterPending(discovered, applied)).isEmpty();
    }

    @Test
    void emptyDiscoveredYieldsEmptyPending() {
        List<MigrationRecord> applied = List.of(applied("0001-create.cql", MigrationRecord.STATUS_SUCCESS));

        assertThat(planner.filterPending(List.of(), applied)).isEmpty();
    }

    private static MigrationFile file(String path) {
        return MigrationFile.fromClasspath(path, path, "desc", "chk-" + path, "cql/app");
    }

    private static MigrationRecord applied(String path, String status) {
        return new MigrationRecord(path, path, "desc", "chk-" + path, Instant.EPOCH, 1L, status, null, 1);
    }
}
