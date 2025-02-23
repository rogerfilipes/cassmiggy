package cassmiggy.internal.schema;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cassmiggy.Config;

/** Initializes the migration infrastructure tables (history and lock) */
public class SchemaBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(SchemaBootstrapper.class);

    public void bootstrap(Config config) {
        CqlSession session = config.getSession();

        createHistoryTable(session, config);
        createLockTable(session, config);

        log.info("Schema bootstrap completed for keyspace: {}", config.getKeyspace());
    }

    private void createHistoryTable(CqlSession session, Config config) {
        String tableName = config.getFullHistoryTableName();

        String ddl = String.format(
                """
            CREATE TABLE IF NOT EXISTS %s (
                partition_id TEXT,
                executed_at TIMESTAMP,
                path TEXT,
                filename TEXT,
                description TEXT,
                checksum TEXT,
                execution_ms BIGINT,
                status TEXT,
                error_message TEXT,
                statements INT,
                PRIMARY KEY (partition_id, executed_at, path)
            ) WITH CLUSTERING ORDER BY (executed_at DESC, path ASC)
                """,
                tableName);

        session.execute(SimpleStatement.builder(ddl)
                .setConsistencyLevel(config.getWriteConsistency())
                .build());

        log.debug("History table {} initialized", tableName);
    }

    private void createLockTable(CqlSession session, Config config) {
        String lockTable = config.getFullLockTableName();

        String ddl = String.format(
                """
            CREATE TABLE IF NOT EXISTS %s (
                lock_id TEXT PRIMARY KEY,
                instance_id TEXT,
                acquired_at TIMESTAMP,
                heartbeat TIMESTAMP
            )
                """,
                lockTable);

        session.execute(SimpleStatement.builder(ddl)
                .setConsistencyLevel(config.getWriteConsistency())
                .build());

        log.debug("Lock table {} initialized", lockTable);
    }
}
