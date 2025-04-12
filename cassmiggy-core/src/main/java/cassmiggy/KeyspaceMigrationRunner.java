package cassmiggy;

import com.datastax.oss.driver.api.core.CqlSession;
import cassmiggy.model.MigrationResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates migrations across one or more keyspaces with sensible defaults, delegating each
 * keyspace to a {@link SchemaMigrator}. The caller supplies the {@link CqlSession}.
 *
 * <p>Keyspaces must already exist; the engine creates only the history and lock tables.
 */
public final class KeyspaceMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(KeyspaceMigrationRunner.class);

    private final CqlSession session;
    private final boolean autoKeyspace;
    private final Duration lockTimeout;
    private final Duration schemaAgreementTimeout;

    private KeyspaceMigrationRunner(Builder builder) {
        this.session = Objects.requireNonNull(builder.session, "session must not be null");
        this.autoKeyspace = builder.autoKeyspace;
        this.lockTimeout = builder.lockTimeout;
        this.schemaAgreementTimeout = builder.schemaAgreementTimeout;
    }

    /** A keyspace to migrate and the location of its migration files (e.g. {@code "cql/app"}). */
    public static final class KeyspaceMigration {

        private final String keyspace;
        private final String location;

        public KeyspaceMigration(String keyspace, String location) {
            Objects.requireNonNull(keyspace, "keyspace must not be null");
            Objects.requireNonNull(location, "location must not be null");
            this.keyspace = keyspace;
            this.location = location;
        }

        public String getKeyspace() {
            return keyspace;
        }

        public String getLocation() {
            return location;
        }
    }

    /** Runs all pending migrations for a single keyspace. */
    public MigrationResult migrate(String keyspace, String location) {
        log.info("Migrating keyspace '{}' from '{}'", keyspace, location);

        Config config = Config.builder()
                .withSession(session)
                .withKeyspace(keyspace)
                .withMigrationsLocation(location)
                .withAutoKeyspace(autoKeyspace)
                .withLockTimeout(lockTimeout)
                .withSchemaAgreementTimeout(schemaAgreementTimeout)
                .build();

        MigrationResult result = new SchemaMigrator(config).migrate();

        log.info(
                "Keyspace '{}': {} migrations applied in {}",
                keyspace,
                result.getAppliedCount(),
                result.getDuration());

        return result;
    }

    /**
     * Runs migrations for each keyspace in order, failing fast: if one throws, the remaining
     * keyspaces are not attempted. To tolerate failures, call {@link #migrate(String, String)}
     * per keyspace and handle errors yourself.
     */
    public List<MigrationResult> migrateAll(List<KeyspaceMigration> migrations) {
        Objects.requireNonNull(migrations, "migrations must not be null");
        List<MigrationResult> results = new ArrayList<>(migrations.size());
        for (KeyspaceMigration migration : migrations) {
            results.add(migrate(migration.getKeyspace(), migration.getLocation()));
        }
        return results;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link KeyspaceMigrationRunner}.
     */
    public static final class Builder {
        private CqlSession session;
        private boolean autoKeyspace = true;
        private Duration lockTimeout = Duration.ofMinutes(5);
        private Duration schemaAgreementTimeout = Duration.ofSeconds(30);

        private Builder() {}

        /** The CQL session to run migrations through. Required. */
        public Builder session(CqlSession session) {
            this.session = session;
            return this;
        }

        /** Whether the engine may treat the configured keyspace as already selectable. Default {@code true}. */
        public Builder autoKeyspace(boolean autoKeyspace) {
            this.autoKeyspace = autoKeyspace;
            return this;
        }

        /** Maximum time to wait to acquire the migration lock. Default 5 minutes. */
        public Builder lockTimeout(Duration lockTimeout) {
            this.lockTimeout = lockTimeout;
            return this;
        }

        /** Maximum time to wait for schema agreement after DDL. Default 30 seconds. */
        public Builder schemaAgreementTimeout(Duration schemaAgreementTimeout) {
            this.schemaAgreementTimeout = schemaAgreementTimeout;
            return this;
        }

        public KeyspaceMigrationRunner build() {
            return new KeyspaceMigrationRunner(this);
        }
    }
}
