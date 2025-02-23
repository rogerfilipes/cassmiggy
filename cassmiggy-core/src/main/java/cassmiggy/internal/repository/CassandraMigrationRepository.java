package cassmiggy.internal.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchableStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.DefaultBatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cassmiggy.Config;
import cassmiggy.model.MigrationRecord;
import cassmiggy.spi.MigrationRepository;

/**
 * Cassandra migration history repository, each keyspace has its own schema_migrations table.
 *
 * <ul>
 *   <li>partition_id: constant value to keep all migrations in one partition</li>
 *   <li>executed_at: clustering column for ordering most recent first</li>
 *   <li>path: clustering column for uniqueness (relative path from migrations root)</li>
 * </ul>
 *
 * <p>When a migration is re-run, after fixing a failed migration, existing records for that
 * path are deleted before inserting the new one.
 */
public class CassandraMigrationRepository implements MigrationRepository {

    private static final Logger log = LoggerFactory.getLogger(CassandraMigrationRepository.class);
    private static final int MAX_ERROR_LENGTH = 10000;
    private static final String PARTITION_ID = "migrations";

    private final CqlSession session;
    private final Config config;
    private final PreparedStatement selectAll;
    private final PreparedStatement selectByPathFiltered;
    private final PreparedStatement deleteByPathAndTimestamp;
    private final PreparedStatement insert;


    public CassandraMigrationRepository(Config config) {
        this.session = config.getSession();
        this.config = config;
        String tableName = config.getFullHistoryTableName();

        this.selectAll = session.prepare(
                SimpleStatement.builder(String.format("SELECT * FROM %s WHERE partition_id = ?", tableName))
                        .setConsistencyLevel(config.getReadConsistency())
                        .build());

        this.selectByPathFiltered = session.prepare(SimpleStatement.builder(String.format(
                        "SELECT * FROM %s WHERE partition_id = ? AND path = ? ALLOW FILTERING", tableName))
                .setConsistencyLevel(config.getReadConsistency())
                .build());

        this.deleteByPathAndTimestamp = session.prepare(SimpleStatement.builder(String.format(
                        "DELETE FROM %s WHERE partition_id = ? AND executed_at = ? AND path = ?", tableName))
                .setConsistencyLevel(config.getWriteConsistency())
                .build());

        this.insert = session.prepare(SimpleStatement.builder(String.format(
                        """
                    INSERT INTO %s (partition_id, executed_at, path, filename, description, checksum,
                        execution_ms, status, error_message, statements)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                        tableName))
                .setConsistencyLevel(config.getWriteConsistency())
                .build());

        log.debug("History repository prepared for table {}", tableName);
    }

    @Override
    public List<MigrationRecord> getAppliedMigrations() {

        ResultSet rs = session.execute(selectAll.bind(PARTITION_ID));

        List<MigrationRecord> migrations = new ArrayList<>();
        for (Row row : rs) {
            migrations.add(toMigrationRecord(row));
        }
        log.debug("Found {} applied migrations", migrations.size());
        return migrations;
    }

    @Override
    public Optional<MigrationRecord> getMigration(String path) {

        ResultSet rs = session.execute(selectByPathFiltered.bind(PARTITION_ID, path));
        Row row = rs.one();
        return row == null ? Optional.empty() : Optional.of(toMigrationRecord(row));
    }

    @Override
    public void recordSuccess(
            String path, String filename, String description, String checksum, long executionMs, int statements) {

        atomicRecordUpdate(
                path, filename, description, checksum, executionMs, MigrationRecord.STATUS_SUCCESS, null, statements);
    }

    @Override
    public void recordFailure(
            String path,
            String filename,
            String description,
            String checksum,
            long executionMs,
            int statements,
            String errorMessage) {
        String error = truncate(errorMessage, MAX_ERROR_LENGTH);
        atomicRecordUpdate(
                path, filename, description, checksum, executionMs, MigrationRecord.STATUS_FAILED, error, statements);
    }


    private void atomicRecordUpdate(
            String path,
            String filename,
            String description,
            String checksum,
            long executionMs,
            String status,
            String errorMessage,
            int statements) {
        ResultSet rs = session.execute(selectByPathFiltered.bind(PARTITION_ID, path));
        List<BatchableStatement<?>> deleteStatements = new ArrayList<>();
        for (Row row : rs) {
            Instant existingTimestamp = row.getInstant("executed_at");
            deleteStatements.add(deleteByPathAndTimestamp.bind(PARTITION_ID, existingTimestamp, path));
        }

        Instant executedAt = Instant.now();
        BoundStatement insertStatement = insert.bind(
                PARTITION_ID,
                executedAt,
                path,
                filename,
                description,
                checksum,
                executionMs,
                status,
                errorMessage,
                statements);

        BatchStatement batch = BatchStatement.builder(DefaultBatchType.LOGGED)
                .addStatements(deleteStatements)
                .addStatement(insertStatement)
                .setConsistencyLevel(config.getWriteConsistency())
                .build();

        session.execute(batch);

        log.debug("Atomically recorded {} as {} (deleted {} previous records)", path, status, deleteStatements.size());
    }

    private MigrationRecord toMigrationRecord(Row row) {
        return new MigrationRecord(
                row.getString("path"),
                row.getString("filename"),
                row.getString("description"),
                row.getString("checksum"),
                row.getInstant("executed_at"),
                row.getLong("execution_ms"),
                row.getString("status"),
                row.getString("error_message"),
                row.getInt("statements"));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 13) + "... [truncated]";
    }
}
