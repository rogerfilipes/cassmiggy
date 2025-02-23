package cassmiggy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;

import cassmiggy.exception.ConfigurationException;
import cassmiggy.model.MissingMigrationBehavior;

/**
 * Configuration for running schema migrations against a Cassandra cluster.
 *
 * <p>Handles locking to ensure only one instance runs migrations at a time,
 * tracks migration history in a dedicated table, and waits for schema agreement
 * across cluster nodes after DDL statements.
 *
 * <p>Use {@link #builder()} to create instances.
 */
public final class Config {

    /**
     * Sealed interface representing the source of migration files.
     * Migrations can come from either the filesystem or the classpath.
     */
    public sealed interface ConfigMigrationSource {
        final class FileSystem implements ConfigMigrationSource {

            private final Path path;

            public FileSystem(Path path) {
                if (path == null) {
                    throw new IllegalArgumentException("path must not be null");
                }
                this.path = path;
            }

            public Path getPath() {
                return path;
            }
        }

        final class Classpath implements ConfigMigrationSource {

            private final String location;

            public Classpath(String location) {
                if (location == null || location.isBlank()) {
                    throw new IllegalArgumentException("location must not be null or blank");
                }
                this.location = location;
            }

            public String getLocation() {
                return location;
            }
        }
    }

    private final CqlSession session;
    private final String keyspace;
    private final ConfigMigrationSource migrationSource;
    private final String historyTable;
    private final String historyKeyspace;
    private final String lockTable;
    private final String lockKeyspace;
    private final Duration lockTimeout;
    private final Duration lockHeartbeatInterval;
    private final Duration staleLockThreshold;
    private final boolean allowStaleLockTakeover;
    private final int heartbeatRetries;
    private final Duration heartbeatRetryInitialDelay;
    private final Duration schemaAgreementTimeout;
    private final Duration schemaAgreementPollInterval;
    private final boolean schemaAgreementOnDdlOnly;
    private final DefaultConsistencyLevel readConsistency;
    private final DefaultConsistencyLevel writeConsistency;
    private final DefaultConsistencyLevel serialConsistency;
    private final String instanceId;
    private final boolean autoKeyspace;
    private final MissingMigrationBehavior missingMigrationBehavior;

    private Config(Builder builder) {
        this.session = builder.session;
        this.keyspace = builder.keyspace;
        this.migrationSource = builder.migrationSource;
        this.historyTable = builder.historyTable;
        this.historyKeyspace = builder.historyKeyspace != null ? builder.historyKeyspace : builder.keyspace;
        this.lockTable = builder.lockTable;
        this.lockKeyspace = builder.lockKeyspace != null ? builder.lockKeyspace : builder.keyspace;
        this.lockTimeout = builder.lockTimeout;
        this.lockHeartbeatInterval = builder.lockHeartbeatInterval;
        this.staleLockThreshold = builder.staleLockThreshold;
        this.allowStaleLockTakeover = builder.allowStaleLockTakeover;
        this.heartbeatRetries = builder.heartbeatRetries;
        this.heartbeatRetryInitialDelay = builder.heartbeatRetryInitialDelay;
        this.schemaAgreementTimeout = builder.schemaAgreementTimeout;
        this.schemaAgreementPollInterval = builder.schemaAgreementPollInterval;
        this.schemaAgreementOnDdlOnly = builder.schemaAgreementOnDdlOnly;
        this.readConsistency = builder.readConsistency;
        this.writeConsistency = builder.writeConsistency;
        this.serialConsistency = builder.serialConsistency;
        this.instanceId = builder.instanceId != null
                ? builder.instanceId
                : UUID.randomUUID().toString();
        this.autoKeyspace = builder.autoKeyspace;
        this.missingMigrationBehavior = builder.missingMigrationBehavior;
    }

    public static Builder builder() {
        return new Builder();
    }

    public CqlSession getSession() {
        return session;
    }

    public String getKeyspace() {
        return keyspace;
    }

    public ConfigMigrationSource getMigrationSource() {
        return migrationSource;
    }

    public Duration getLockTimeout() {
        return lockTimeout;
    }

    public Duration getLockHeartbeatInterval() {
        return lockHeartbeatInterval;
    }

    public Duration getStaleLockThreshold() {
        return staleLockThreshold;
    }

    public boolean isAllowStaleLockTakeover() {
        return allowStaleLockTakeover;
    }

    /**
     * Returns the number of retries for heartbeat updates on transient failures.
     * Total attempts = retries + 1.
     *
     * @return the number of retries (default: 3)
     */
    public int getHeartbeatRetries() {
        return heartbeatRetries;
    }

    /**
     * Returns the initial delay before the first heartbeat retry.
     * Subsequent retries use exponential backoff (2x growth, capped at 5s).
     *
     * @return the initial retry delay (default: 100ms)
     */
    public Duration getHeartbeatRetryInitialDelay() {
        return heartbeatRetryInitialDelay;
    }

    public Duration getSchemaAgreementTimeout() {
        return schemaAgreementTimeout;
    }

    public Duration getSchemaAgreementPollInterval() {
        return schemaAgreementPollInterval;
    }

    public boolean isSchemaAgreementOnDdlOnly() {
        return schemaAgreementOnDdlOnly;
    }

    public DefaultConsistencyLevel getReadConsistency() {
        return readConsistency;
    }

    public DefaultConsistencyLevel getWriteConsistency() {
        return writeConsistency;
    }

    public DefaultConsistencyLevel getSerialConsistency() {
        return serialConsistency;
    }

    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Returns whether auto-keyspace is enabled.
     * When enabled, migration statements are executed with the configured keyspace context,
     * allowing unqualified table names in CQL files.
     *
     * @return true if auto-keyspace is enabled
     */
    public boolean isAutoKeyspace() {
        return autoKeyspace;
    }

    /**
     * Returns the behavior when a previously applied migration file is missing from sources.
     *
     * @return the missing migration behavior (default: {@link MissingMigrationBehavior#FAIL})
     * @see MissingMigrationBehavior
     */
    public MissingMigrationBehavior getMissingMigrationBehavior() {
        return missingMigrationBehavior;
    }

    public String getFullHistoryTableName() {
        return historyKeyspace + "." + historyTable;
    }

    public String getFullLockTableName() {
        return lockKeyspace + "." + lockTable;
    }

    public boolean isFileSystemSource() {
        return migrationSource instanceof ConfigMigrationSource.FileSystem;
    }

    public boolean isClasspathSource() {
        return migrationSource instanceof ConfigMigrationSource.Classpath;
    }

    /**
     * Quotes a CQL identifier if it contains uppercase characters or doesn't match
     * the standard lowercase identifier pattern. In CQL, unquoted identifiers are
     * case-insensitive and folded to lowercase. To preserve case, identifiers must
     * be double-quoted.
     *
     * @param identifier the CQL identifier (keyspace or table name)
     * @return the identifier, quoted if necessary
     */
    static String quoteIfNeeded(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return identifier;
        }
        if (identifier.startsWith("\"") && identifier.endsWith("\"")) {
            return identifier;
        }
        if (!identifier.equals(identifier.toLowerCase()) || !identifier.matches("[a-z_][a-z0-9_]*")) {
            return "\"" + identifier.replace("\"", "\"\"") + "\"";
        }
        return identifier;
    }

    public static final class Builder {

        private static final String DEFAULT_HISTORY_TABLE = "schema_migration_history";
        private static final String DEFAULT_LOCK_TABLE = "schema_migration_lock";
        private static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofMinutes(5);
        private static final Duration DEFAULT_LOCK_HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
        private static final Duration DEFAULT_STALE_LOCK_THRESHOLD = Duration.ofMinutes(10);
        private static final boolean DEFAULT_ALLOW_STALE_LOCK_TAKEOVER = false;
        private static final Duration DEFAULT_SCHEMA_AGREEMENT_TIMEOUT = Duration.ofSeconds(30);
        private static final Duration DEFAULT_SCHEMA_AGREEMENT_POLL_INTERVAL = Duration.ofMillis(500);
        private static final boolean DEFAULT_SCHEMA_AGREEMENT_ON_DDL_ONLY = true;
        private static final DefaultConsistencyLevel DEFAULT_READ_CONSISTENCY = DefaultConsistencyLevel.LOCAL_QUORUM;
        private static final DefaultConsistencyLevel DEFAULT_WRITE_CONSISTENCY = DefaultConsistencyLevel.LOCAL_QUORUM;
        private static final DefaultConsistencyLevel DEFAULT_SERIAL_CONSISTENCY = DefaultConsistencyLevel.LOCAL_SERIAL;
        private static final int DEFAULT_HEARTBEAT_RETRIES = 3;
        private static final Duration DEFAULT_HEARTBEAT_RETRY_INITIAL_DELAY = Duration.ofMillis(100);
        private static final MissingMigrationBehavior DEFAULT_MISSING_MIGRATION_BEHAVIOR =
                MissingMigrationBehavior.FAIL;

        private CqlSession session;
        private String keyspace;
        private ConfigMigrationSource migrationSource;
        private String historyTable = DEFAULT_HISTORY_TABLE;
        private String historyKeyspace;
        private String lockTable = DEFAULT_LOCK_TABLE;
        private String lockKeyspace;
        private Duration lockTimeout = DEFAULT_LOCK_TIMEOUT;
        private Duration lockHeartbeatInterval = DEFAULT_LOCK_HEARTBEAT_INTERVAL;
        private Duration staleLockThreshold = DEFAULT_STALE_LOCK_THRESHOLD;
        private boolean allowStaleLockTakeover = DEFAULT_ALLOW_STALE_LOCK_TAKEOVER;
        private Duration schemaAgreementTimeout = DEFAULT_SCHEMA_AGREEMENT_TIMEOUT;
        private Duration schemaAgreementPollInterval = DEFAULT_SCHEMA_AGREEMENT_POLL_INTERVAL;
        private boolean schemaAgreementOnDdlOnly = DEFAULT_SCHEMA_AGREEMENT_ON_DDL_ONLY;
        private DefaultConsistencyLevel readConsistency = DEFAULT_READ_CONSISTENCY;
        private DefaultConsistencyLevel writeConsistency = DEFAULT_WRITE_CONSISTENCY;
        private DefaultConsistencyLevel serialConsistency = DEFAULT_SERIAL_CONSISTENCY;
        private String instanceId;
        private boolean autoKeyspace = true;
        private int heartbeatRetries = DEFAULT_HEARTBEAT_RETRIES;
        private Duration heartbeatRetryInitialDelay = DEFAULT_HEARTBEAT_RETRY_INITIAL_DELAY;
        private MissingMigrationBehavior missingMigrationBehavior = DEFAULT_MISSING_MIGRATION_BEHAVIOR;

        private Builder() {}

        public Builder withSession(CqlSession session) {
            this.session = session;
            return this;
        }

        /**
         * Sets the main keyspace for migrations.
         * CamelCase or mixed-case keyspace names will be automatically quoted.
         *
         * @param keyspace the keyspace name (required)
         * @return this builder
         */
        public Builder withKeyspace(String keyspace) {
            this.keyspace = quoteIfNeeded(keyspace);
            return this;
        }

        public Builder withMigrationsLocation(Path path) {
            this.migrationSource = new ConfigMigrationSource.FileSystem(path);
            return this;
        }

        public Builder withMigrationsLocation(String classpath) {
            this.migrationSource = new ConfigMigrationSource.Classpath(classpath);
            return this;
        }

        /**
         * Sets the history table name.
         * CamelCase or mixed-case table names will be automatically quoted.
         *
         * @param tableName the table name (default: "schema_migrations")
         * @return this builder
         */
        public Builder withHistoryTable(String tableName) {
            this.historyTable = quoteIfNeeded(tableName);
            return this;
        }

        /**
         * Sets the keyspace for the history table.
         *
         * <p>Use a separate keyspace when multiple applications share the main keyspace
         * but need independent migration tracking, or when the history table requires
         * different replication settings.
         *
         * <p>CamelCase or mixed-case keyspace names will be automatically quoted.
         *
         * @param keyspace the keyspace name (default: same as main keyspace)
         * @return this builder
         */
        public Builder withHistoryKeyspace(String keyspace) {
            this.historyKeyspace = quoteIfNeeded(keyspace);
            return this;
        }

        /**
         * Sets the lock table name.
         * CamelCase or mixed-case table names will be automatically quoted.
         *
         * @param tableName the table name (default: "migration_locks")
         * @return this builder
         */
        public Builder withLockTable(String tableName) {
            this.lockTable = quoteIfNeeded(tableName);
            return this;
        }

        /**
         * Sets the keyspace for the lock table.
         *
         * <p>Use a separate keyspace when multiple applications share the main keyspace
         * but need independent locking, or when the lock table requires
         * different replication settings.
         *
         * <p>CamelCase or mixed-case keyspace names will be automatically quoted.
         *
         * @param keyspace the keyspace name (default: same as main keyspace)
         * @return this builder
         */
        public Builder withLockKeyspace(String keyspace) {
            this.lockKeyspace = quoteIfNeeded(keyspace);
            return this;
        }

        /**
         * Sets the maximum time to wait for acquiring the lock.
         *
         * @param timeout the lock timeout (default: 5 minutes)
         * @return this builder
         */
        public Builder withLockTimeout(Duration timeout) {
            this.lockTimeout = timeout;
            return this;
        }

        /**
         * Sets the interval for lock heartbeat updates.
         *
         * <p>While migrations run, a background thread updates the lock timestamp
         * at this interval. If heartbeats stop (crash, network issue), the lock
         * becomes stale after {@link #withStaleLockThreshold(Duration)}.
         *
         * <p>Must be less than staleLockThreshold.
         *
         * @param interval the heartbeat interval (default: 30 seconds)
         * @return this builder
         */
        public Builder withLockHeartbeatInterval(Duration interval) {
            this.lockHeartbeatInterval = interval;
            return this;
        }

        /**
         * Sets the threshold after which a lock is considered stale.
         *
         * <p>A lock is stale when its last heartbeat is older than this threshold.
         * Must be greater than {@link #withLockHeartbeatInterval(Duration)}.
         *
         * <p>Recommended: at least 3x the heartbeat interval to tolerate transient failures.
         *
         * @param threshold the stale lock threshold (default: 10 minutes)
         * @return this builder
         */
        public Builder withStaleLockThreshold(Duration threshold) {
            this.staleLockThreshold = threshold;
            return this;
        }

        /**
         * Sets whether to allow taking over stale locks automatically.
         *
         * <p>When true: if a lock is stale (no heartbeat for longer than staleLockThreshold),
         * another instance can claim it and proceed with migrations.
         *
         * <p>Risk: if the original holder is still running (e.g., network partition caused
         * heartbeat failures), two instances may run migrations concurrently.
         *
         * <p>When false (default): stale locks require manual intervention.
         *
         * @param allow true to allow stale lock takeover (default: false)
         * @return this builder
         */
        public Builder withAllowStaleLockTakeover(boolean allow) {
            this.allowStaleLockTakeover = allow;
            return this;
        }

        /**
         * Sets the number of retries for lock heartbeat updates on transient failures.
         * The total number of attempts will be retries + 1.
         *
         * @param retries the number of retries (default: 3, minimum: 1)
         * @return this builder
         */
        public Builder withHeartbeatRetries(int retries) {
            this.heartbeatRetries = retries;
            return this;
        }

        /**
         * Sets the initial delay before the first heartbeat retry.
         * Subsequent retries use exponential backoff (delay * 2^attempt), capped at 5 seconds.
         *
         * @param delay the initial retry delay (default: 100ms)
         * @return this builder
         */
        public Builder withHeartbeatRetryInitialDelay(Duration delay) {
            this.heartbeatRetryInitialDelay = delay;
            return this;
        }

        /**
         * Sets the timeout for waiting for schema agreement.
         *
         * @param timeout the schema agreement timeout (default: 30 seconds)
         * @return this builder
         */
        public Builder withSchemaAgreementTimeout(Duration timeout) {
            this.schemaAgreementTimeout = timeout;
            return this;
        }

        /**
         * Sets the interval for polling schema agreement status.
         *
         * @param interval the poll interval (default: 500ms)
         * @return this builder
         */
        public Builder withSchemaAgreementPollInterval(Duration interval) {
            this.schemaAgreementPollInterval = interval;
            return this;
        }

        /**
         * Sets whether to wait for schema agreement only after DDL statements.
         *
         * <p>DDL statements are: CREATE, ALTER, DROP on KEYSPACE, TABLE, INDEX, TYPE,
         * FUNCTION, AGGREGATE, MATERIALIZED VIEW, TRIGGER, ROLE, USER.
         *
         * <p>When true (default): waits for agreement only after DDL.
         * When false: waits after every statement including DML (rarely useful).
         *
         * @param ddlOnly true to check only on DDL (default: true)
         * @return this builder
         * @see cassmiggy.spi.SchemaAgreementChecker
         */
        public Builder withSchemaAgreementOnDdlOnly(boolean ddlOnly) {
            this.schemaAgreementOnDdlOnly = ddlOnly;
            return this;
        }

        /**
         * Sets the consistency level for read operations.
         *
         * <p>Used when reading migration history and lock state.
         * Lower levels (ONE, LOCAL_ONE) risk reading stale data.
         *
         * @param consistency the read consistency level (default: LOCAL_QUORUM)
         * @return this builder
         */
        public Builder withReadConsistency(DefaultConsistencyLevel consistency) {
            this.readConsistency = consistency;
            return this;
        }

        /**
         * Sets the consistency level for write operations.
         *
         * <p>Used when recording migration results, creating internal tables,
         * and non-conditional lock updates.
         *
         * @param consistency the write consistency level (default: LOCAL_QUORUM)
         * @return this builder
         */
        public Builder withWriteConsistency(DefaultConsistencyLevel consistency) {
            this.writeConsistency = consistency;
            return this;
        }

        /**
         * Sets the serial consistency level for conditional writes (IF NOT EXISTS, IF condition).
         *
         * <p>Used by lock operations: acquire, release, heartbeat, and takeover.
         * These operations use conditional writes to ensure atomicity.
         *
         * <p>Use LOCAL_SERIAL for single-datacenter deployments.
         * Use SERIAL for multi-datacenter deployments where migrations may run from different
         * datacenters simultaneously - LOCAL_SERIAL in multi-DC can cause split-brain locks.
         *
         * @param consistency the serial consistency level (default: LOCAL_SERIAL)
         * @return this builder
         */
        public Builder withSerialConsistency(DefaultConsistencyLevel consistency) {
            this.serialConsistency = consistency;
            return this;
        }

        /**
         * Sets the instance identifier for this migration run.
         * Default: auto-generated UUID.
         */
        public Builder withInstanceId(String instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        /**
         * Sets whether to automatically use the configured keyspace for migration statements.
         * When enabled, CQL statements are executed with the keyspace context, allowing
         * unqualified table names (e.g., "users" instead of "my_keyspace.users").
         *
         * @param autoKeyspace true to enable auto-keyspace (default: true)
         * @return this builder
         */
        public Builder withAutoKeyspace(boolean autoKeyspace) {
            this.autoKeyspace = autoKeyspace;
            return this;
        }

        /**
         * Sets the behavior when a previously applied migration file is not found in sources.
         *
         * <p>Options:
         * <ul>
         *   <li>{@link MissingMigrationBehavior#FAIL} - Throw exception, stop migration (default)</li>
         *   <li>{@link MissingMigrationBehavior#WARN} - Log warning, continue</li>
         *   <li>{@link MissingMigrationBehavior#IGNORE} - Skip silently, no logging</li>
         * </ul>
         *
         * @param behavior the desired behavior for missing migration files
         * @return this builder
         * @see MissingMigrationBehavior
         */
        public Builder withMissingMigrationBehavior(MissingMigrationBehavior behavior) {
            this.missingMigrationBehavior = behavior;
            return this;
        }

        /**
         * @throws ConfigurationException if validation fails
         */
        public Config build() {
            validate();
            return new Config(this);
        }

        private void validate() {
            validateRequiredFields();
            validateDurations();
            validateConsistencyLevels();
            validateHeartbeatConfiguration();
        }

        private void validateRequiredFields() {
            if (session == null) {
                throw new ConfigurationException("CqlSession is required");
            }

            if (keyspace == null || keyspace.isBlank()) {
                throw new ConfigurationException("Keyspace is required");
            }

            if (migrationSource == null) {
                throw new ConfigurationException("migrationsLocation must be provided");
            }

            if (historyTable == null || historyTable.isBlank()) {
                throw new ConfigurationException("History table name cannot be empty");
            }

            if (lockTable == null || lockTable.isBlank()) {
                throw new ConfigurationException("Lock table name cannot be empty");
            }

            if (missingMigrationBehavior == null) {
                throw new ConfigurationException("Missing migration behavior cannot be null");
            }
        }

        private void validateDurations() {
            requirePositiveDuration(lockTimeout, "Lock timeout");
            requirePositiveDuration(lockHeartbeatInterval, "Lock heartbeat interval");
            requirePositiveDuration(staleLockThreshold, "Stale lock threshold");
            requirePositiveDuration(schemaAgreementTimeout, "Schema agreement timeout");
            requirePositiveDuration(schemaAgreementPollInterval, "Schema agreement poll interval");
            requirePositiveDuration(heartbeatRetryInitialDelay, "Heartbeat retry initial delay");
        }

        private static void requirePositiveDuration(Duration duration, String name) {
            if (duration == null || duration.isNegative() || duration.isZero()) {
                throw new ConfigurationException(name + " must be a positive duration");
            }
        }

        private void validateConsistencyLevels() {
            if (readConsistency == null) {
                throw new ConfigurationException("Read consistency level is required");
            }

            if (writeConsistency == null) {
                throw new ConfigurationException("Write consistency level is required");
            }

            if (serialConsistency == null) {
                throw new ConfigurationException("Serial consistency level is required");
            }

            if (serialConsistency != DefaultConsistencyLevel.SERIAL
                    && serialConsistency != DefaultConsistencyLevel.LOCAL_SERIAL) {
                throw new ConfigurationException(
                        "Serial consistency must be SERIAL or LOCAL_SERIAL, got: " + serialConsistency);
            }
        }

        private void validateHeartbeatConfiguration() {
            if (lockHeartbeatInterval.compareTo(staleLockThreshold) >= 0) {
                throw new ConfigurationException("Lock heartbeat interval must be less than stale lock threshold");
            }

            if (heartbeatRetries < 1) {
                throw new ConfigurationException("Heartbeat retries must be at least 1");
            }
        }
    }
}
