package cassmiggy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cassmiggy.exception.ChecksumMismatchException;
import cassmiggy.model.MigrationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;

/**
 * Migration scenarios run against a real Cassandra container. Subclasses supply a session bound
 * to a specific Cassandra version so the same scenarios are exercised on both the protocol-V4
 * (Cassandra 3) and protocol-V5 (Cassandra 4) code paths.
 */
abstract class AbstractMigrationIntegrationTest {

    private static final AtomicInteger KS_COUNTER = new AtomicInteger();

    /** A session connected to the container (no default keyspace). */
    protected abstract CqlSession session();

    private String newKeyspace() {
        String ks = "cassmiggy_it_" + KS_COUNTER.incrementAndGet();
        session().execute("CREATE KEYSPACE IF NOT EXISTS " + ks
                + " WITH replication = {'class':'SimpleStrategy','replication_factor':1}");
        return ks;
    }

    private SchemaMigrator migrator(String keyspace, String classpathLocation) {
        Config config = Config.builder()
                .withSession(session())
                .withKeyspace(keyspace)
                .withMigrationsLocation(classpathLocation)
                .withAutoKeyspace(true)
                .build();
        return new SchemaMigrator(config);
    }

    private long count(String keyspace, String table) {
        Row row = session().execute("SELECT COUNT(*) FROM " + keyspace + "." + table).one();
        return row == null ? 0 : row.getLong(0);
    }

    @Test
    void appliesPendingMigrationsAndRecordsHistory() {
        String ks = newKeyspace();

        MigrationResult result = migrator(ks, "migrations").migrate();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAppliedCount()).isEqualTo(3);
        // The DML migration seeded two users
        assertThat(count(ks, "users")).isEqualTo(2);
        // Three SUCCESS rows in the history table
        long successes = session()
                .execute("SELECT status FROM " + ks + ".schema_migration_history").all().stream()
                .filter(r -> "SUCCESS".equals(r.getString("status")))
                .count();
        assertThat(successes).isEqualTo(3);
    }

    @Test
    void secondRunIsNoOp() {
        String ks = newKeyspace();

        migrator(ks, "migrations").migrate();
        SchemaMigrator second = migrator(ks, "migrations");
        MigrationResult result = second.migrate();

        assertThat(result.getAppliedCount()).isZero();
        assertThat(second.getStatus().getPending()).isEmpty();
        assertThat(second.getStatus().isUpToDate()).isTrue();
    }

    @Test
    void reportsPendingBeforeApplying() {
        String ks = newKeyspace();
        SchemaMigrator migrator = migrator(ks, "migrations");

        assertThat(migrator.getStatus().getPending()).hasSize(3);
        assertThat(migrator.getStatus().getApplied()).isEmpty();

        migrator.migrate();

        assertThat(migrator.getStatus().getApplied()).hasSize(3);
        assertThat(migrator.getStatus().getPending()).isEmpty();
    }

    @Test
    void createsHistoryAndLockTables() {
        String ks = newKeyspace();

        migrator(ks, "migrations").migrate();

        var keyspaceMetadata = session().getMetadata().getKeyspace(ks).orElseThrow();
        assertThat(keyspaceMetadata.getTable("schema_migration_history")).isPresent();
        assertThat(keyspaceMetadata.getTable("schema_migration_lock")).isPresent();
    }

    @Test
    void constructorDoesNotCreateInfrastructureTables() {
        String ks = newKeyspace();

        migrator(ks, "migrations");

        var keyspaceMetadata = session().getMetadata().getKeyspace(ks).orElseThrow();
        assertThat(keyspaceMetadata.getTable("schema_migration_history")).isEmpty();
        assertThat(keyspaceMetadata.getTable("schema_migration_lock")).isEmpty();
    }

    @Test
    void detectsChecksumMismatchWhenAnAppliedFileChanges(@TempDir Path dir) throws IOException {
        String ks = newKeyspace();
        Path migration = dir.resolve("0001-create-thing.cql");
        Files.writeString(migration, "CREATE TABLE thing (id int PRIMARY KEY);\n");

        Config first = Config.builder()
                .withSession(session())
                .withKeyspace(ks)
                .withMigrationsLocation(dir)
                .withAutoKeyspace(true)
                .build();
        MigrationResult applied = new SchemaMigrator(first).migrate();
        assertThat(applied.getAppliedCount()).isEqualTo(1);

        // Tamper with the already-applied file
        Files.writeString(migration, "CREATE TABLE thing (id int PRIMARY KEY, extra text);\n");

        Config second = Config.builder()
                .withSession(session())
                .withKeyspace(ks)
                .withMigrationsLocation(dir)
                .withAutoKeyspace(true)
                .build();
        SchemaMigrator migrator = new SchemaMigrator(second);

        assertThatThrownBy(migrator::migrate).isInstanceOf(ChecksumMismatchException.class);
    }
}
