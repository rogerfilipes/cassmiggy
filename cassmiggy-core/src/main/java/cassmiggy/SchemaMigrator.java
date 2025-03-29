package cassmiggy;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cassmiggy.exception.LockLostException;
import cassmiggy.exception.MigrationException;
import cassmiggy.internal.discovery.ClasspathDiscovery;
import cassmiggy.internal.discovery.FileSystemDiscovery;
import cassmiggy.internal.engine.ExecutionResult;
import cassmiggy.internal.engine.MigrationExecutionException;
import cassmiggy.internal.engine.MigrationExecutor;
import cassmiggy.internal.engine.MigrationFailureHandler;
import cassmiggy.internal.engine.StatementExecutor;
import cassmiggy.internal.integrity.Sha256ChecksumProvider;
import cassmiggy.internal.lock.CassandraLock;
import cassmiggy.internal.parser.AntlrCqlParser;
import cassmiggy.internal.planning.MigrationPlanner;
import cassmiggy.internal.repository.CassandraLockRepository;
import cassmiggy.internal.repository.CassandraMigrationRepository;
import cassmiggy.internal.schema.DriverSchemaAgreementChecker;
import cassmiggy.internal.schema.SchemaBootstrapper;
import cassmiggy.internal.validation.MigrationValidator;
import cassmiggy.model.MigrationFile;
import cassmiggy.model.MigrationRecord;
import cassmiggy.model.MigrationResult;
import cassmiggy.model.MigrationStatus;
import cassmiggy.spi.ChecksumProvider;
import cassmiggy.spi.LockRepository;
import cassmiggy.spi.MigrationDiscovery;
import cassmiggy.spi.MigrationLock;
import cassmiggy.spi.MigrationParser;
import cassmiggy.spi.MigrationRepository;
import cassmiggy.spi.SchemaAgreementChecker;

/**
 * The migration engine - the default {@link Migrator}.
 *
 *
 * Use {@link KeyspaceMigrationRunner} to use multiple keyspaces.
 */
public class SchemaMigrator implements Migrator {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);

    private final Config config;
    private final MigrationParser parser;
    private final MigrationValidator validator;
    private final MigrationPlanner planner;
    private final SchemaBootstrapper bootstrapper;
    private volatile MigrationDiscovery discovery;
    private volatile MigrationRepository repository;
    private volatile MigrationLock lock;
    private volatile MigrationExecutor executor;
    private volatile MigrationFailureHandler failureHandler;
    private volatile boolean initialized;

    /**
     * Creates a new migrator with default dependencies.
     *
     * <p>Need to call SchemaMigrator::migrate() to initiate the process</p>
     */
    public SchemaMigrator(Config config) {
        this(config, new AntlrCqlParser());
    }

    /**
     * Creates a new migrator with default dependencies and a custom parser.
     *
     * <p>Need to call SchemaMigrator::migrate() to initiate the process</p>
     */
    public SchemaMigrator(Config config, MigrationParser parser) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.bootstrapper = new SchemaBootstrapper();
        this.validator = new MigrationValidator(config.getMissingMigrationBehavior());
        this.planner = new MigrationPlanner();
    }

    /**
     * Creates a new migrator with individual custom dependencies and a custom parser.
     */
    public SchemaMigrator(
            Config config,
            MigrationDiscovery discovery,
            MigrationRepository repository,
            MigrationLock lock,
            MigrationParser parser) {
        this(config, parser);
        buildEngine(discovery, repository, lock, parser);
    }

    private void buildEngine(
            MigrationDiscovery discovery,
            MigrationRepository repository,
            MigrationLock lock,
            MigrationParser parser) {
        Objects.requireNonNull(discovery, "discovery must not be null");
        Objects.requireNonNull(repository, "repository must not be null");
        Objects.requireNonNull(lock, "lock must not be null");
        Objects.requireNonNull(parser, "parser must not be null");

        SchemaAgreementChecker agreementChecker = new DriverSchemaAgreementChecker(config.getSession());
        String executorKeyspace = config.isAutoKeyspace() ? config.getKeyspace() : null;
        StatementExecutor statementExecutor =
                new StatementExecutor(config.getSession(), config.getWriteConsistency(), executorKeyspace);

        this.discovery = discovery;
        this.repository = repository;
        this.lock = lock;

        this.executor = new MigrationExecutor(config, discovery, parser, statementExecutor, agreementChecker);
        this.failureHandler = new MigrationFailureHandler(repository);
    }

    private void initialize() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            bootstrap();
            if (discovery == null) {
                buildDefaultEngine();
            }
            initialized = true;
        }
    }

    private void buildDefaultEngine() {
        ChecksumProvider checksumProvider = new Sha256ChecksumProvider();
        MigrationDiscovery discovery = createDiscovery(config, checksumProvider);
        MigrationRepository repository = new CassandraMigrationRepository(config);
        LockRepository lockRepository = new CassandraLockRepository(config);
        MigrationLock lock = new CassandraLock(config, lockRepository);

        buildEngine(discovery, repository, lock, parser);
    }

    private static MigrationDiscovery createDiscovery(Config config, ChecksumProvider checksumProvider) {
        Config.ConfigMigrationSource source = config.getMigrationSource();
        if (source instanceof Config.ConfigMigrationSource.FileSystem fs) {
            return new FileSystemDiscovery(fs.getPath(), checksumProvider);
        } else if (source instanceof Config.ConfigMigrationSource.Classpath cp) {
            return new ClasspathDiscovery(cp.getLocation(), checksumProvider);
        }
        throw new IllegalStateException("Unknown migration source type: " + source);
    }

    /** Creates the history and lock tables if they don't exist. */
    public void bootstrap() {
        bootstrapper.bootstrap(config);
    }

    @Override
    public MigrationResult migrate() {
        initialize();
        log.info("Starting migration for keyspace: {}", config.getKeyspace());
        Instant startTime = Instant.now();
        MigrationResult.Builder result = MigrationResult.builder().startedAt(startTime);

        try {
            lock.acquire(config.getLockTimeout());

            try {
                lock.startHeartbeat();

                List<MigrationFile> discovered = discovery.discover();
                log.info("Discovered {} migration files", discovered.size());

                List<MigrationRecord> applied = repository.getAppliedMigrations();
                validator.validateChecksums(discovered, applied);

                List<MigrationFile> pending = planner.filterPending(discovered, applied);

                if (pending.isEmpty()) {
                    log.info("No pending migrations to apply");
                    return result.completedAt(Instant.now()).build();
                }

                log.info("{} pending migrations to apply", pending.size());

                for (MigrationFile migration : pending) {
                    if (!lock.isHeld()) {
                        throw new LockLostException(migration.getFilename());
                    }
                    executeMigration(migration, result);
                }

            } finally {
                lock.stopHeartbeat();
                lock.release();
                log.debug("Lock released");
            }

            log.info(
                    "Migration completed successfully: {} migrations applied",
                    result.build().getAppliedCount());
            return result.completedAt(Instant.now()).build();

        } catch (Exception e) {
            result.incrementFailed();
            log.error("Migration failed: {}", e.getMessage());
            throw e;
        }
    }

    private void executeMigration(MigrationFile migration, MigrationResult.Builder result) {
        Instant migrationStart = Instant.now();
        int executedStatements = 0;

        try {
            ExecutionResult execResult = executor.execute(migration);
            executedStatements = execResult.getStatementCount();

            repository.recordSuccess(
                    migration.getPath(),
                    migration.getFilename(),
                    migration.getDescription(),
                    migration.getChecksum(),
                    execResult.getExecutionTime().toMillis(),
                    execResult.getStatementCount());

            result.addApplied(
                    migration.getPath(), migration.getDescription(), execResult.getStatementCount(), execResult.getExecutionTime());

        } catch (MigrationExecutionException e) {
            executedStatements = e.getExecutedStatements();
            MigrationException cause = e.getMigrationException();
            failureHandler.handleFailure(migration, migrationStart, executedStatements, cause);
            throw cause;
        } catch (MigrationException e) {
            // Record ALL migration failures (parse errors, validation errors, execution errors, etc.)
            failureHandler.handleFailure(migration, migrationStart, executedStatements, e);
            throw e;
        }
    }

    @Override
    public MigrationStatus getStatus() {
        initialize();
        log.debug("Getting migration status for keyspace: {}", config.getKeyspace());

        List<MigrationFile> discovered = discovery.discover();
        List<MigrationRecord> allRecords = repository.getAppliedMigrations();

        List<MigrationRecord> applied =
                allRecords.stream().filter(MigrationRecord::isSuccess).toList();

        List<MigrationRecord> failed =
                allRecords.stream().filter(MigrationRecord::isFailed).toList();

        List<MigrationFile> pending = planner.filterPending(discovered, allRecords);

        return new MigrationStatus(applied, pending, failed);
    }
}
