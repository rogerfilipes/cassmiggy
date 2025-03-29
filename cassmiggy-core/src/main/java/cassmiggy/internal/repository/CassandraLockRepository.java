package cassmiggy.internal.repository;

import java.util.Optional;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cassmiggy.Config;
import cassmiggy.internal.lock.LockInfo;
import cassmiggy.spi.LockRepository;

public class CassandraLockRepository implements LockRepository {

    private static final Logger log = LoggerFactory.getLogger(CassandraLockRepository.class);
    private static final String APPLIED_COLUMN = "[applied]";

    private final CqlSession session;
    private final Config config;
    private final Object prepareLock = new Object();

    private volatile boolean prepared = false;
    private volatile PreparedStatement acquireStmt;
    private volatile PreparedStatement releaseStmt;
    private volatile PreparedStatement heartbeatStmt;
    private volatile PreparedStatement selectStmt;
    private volatile PreparedStatement takeoverStmt;

    public CassandraLockRepository(Config config) {
        this.session = config.getSession();
        this.config = config;
    }


    private void prepareStatementsIfNeeded() {
        if (!prepared) {
            synchronized (prepareLock) {
                if (!prepared) {
                    prepareStatements();
                    prepared = true;
                }
            }
        }
    }

    private void prepareStatements() {
        String lockTable = config.getFullLockTableName();
        DefaultConsistencyLevel readCl = config.getReadConsistency();
        DefaultConsistencyLevel writeCl = config.getWriteConsistency();
        DefaultConsistencyLevel serialCl = config.getSerialConsistency();

        this.acquireStmt = session.prepare(SimpleStatement.builder(String.format(
                        """
                    INSERT INTO %s (lock_id, instance_id, acquired_at, heartbeat)
                    VALUES (?, ?, toTimestamp(now()), toTimestamp(now()))
                    IF NOT EXISTS
                    """,
                        lockTable))
                .setConsistencyLevel(writeCl)
                .setSerialConsistencyLevel(serialCl)
                .build());

        this.releaseStmt = session.prepare(
                SimpleStatement.builder(String.format("DELETE FROM %s WHERE lock_id = ? IF instance_id = ?", lockTable))
                        .setConsistencyLevel(writeCl)
                        .setSerialConsistencyLevel(serialCl)
                        .build());

        this.heartbeatStmt = session.prepare(SimpleStatement.builder(String.format(
                        "UPDATE %s SET heartbeat = toTimestamp(now()) WHERE lock_id = ? IF instance_id = ?", lockTable))
                .setConsistencyLevel(writeCl)
                .setSerialConsistencyLevel(serialCl)
                .build());

        this.selectStmt = session.prepare(SimpleStatement.builder(String.format(
                        "SELECT lock_id, instance_id, acquired_at, heartbeat FROM %s WHERE lock_id = ?", lockTable))
                .setConsistencyLevel(readCl)
                .build());

        this.takeoverStmt = session.prepare(SimpleStatement.builder(String.format(
                        """
                    UPDATE %s SET instance_id = ?, acquired_at = toTimestamp(now()), heartbeat = toTimestamp(now())
                    WHERE lock_id = ?
                    IF instance_id = ?
                    """,
                        lockTable))
                .setConsistencyLevel(writeCl)
                .setSerialConsistencyLevel(serialCl)
                .build());

        log.debug(
                "Lock repository prepared for table {} with read consistency {}, write consistency {}, serial consistency {}",
                lockTable, readCl, writeCl, serialCl);
    }

    @Override
    public boolean tryAcquire(String lockId, String owner) {
        prepareStatementsIfNeeded();
        ResultSet rs = session.execute(acquireStmt.bind(lockId, owner));
        Row row = rs.one();

        boolean applied = row != null && row.getBoolean(APPLIED_COLUMN);
        if (applied) {
            log.debug("Lock {} acquired by {}", lockId, owner);
        } else {
            log.debug("Lock {} not acquired - already held", lockId);
        }
        return applied;
    }

    @Override
    public boolean release(String lockId, String owner) {
        prepareStatementsIfNeeded();
        ResultSet rs = session.execute(releaseStmt.bind(lockId, owner));
        Row row = rs.one();

        boolean applied = row != null && row.getBoolean(APPLIED_COLUMN);
        if (applied) {
            log.debug("Lock {} released by {}", lockId, owner);
        } else {
            log.debug("Lock {} not released - not owned by {}", lockId, owner);
        }
        return applied;
    }

    @Override
    public boolean updateHeartbeat(String lockId, String owner) {
        prepareStatementsIfNeeded();
        ResultSet rs = session.execute(heartbeatStmt.bind(lockId, owner));
        Row row = rs.one();

        boolean applied = row != null && row.getBoolean(APPLIED_COLUMN);
        if (applied) {
            log.trace("Heartbeat updated for lock {}", lockId);
        } else {
            log.warn("Heartbeat update failed for lock {} - not owned by {}", lockId, owner);
        }
        return applied;
    }

    @Override
    public Optional<LockInfo> getLockInfo(String lockId) {
        prepareStatementsIfNeeded();
        ResultSet rs = session.execute(selectStmt.bind(lockId));
        Row row = rs.one();

        if (row == null) {
            return Optional.empty();
        }

        return Optional.of(new LockInfo(
                row.getString("lock_id"),
                row.getString("instance_id"),
                row.getInstant("acquired_at"),
                row.getInstant("heartbeat")));
    }

    @Override
    public boolean tryTakeover(String lockId, String newOwner, String expectedOwner) {
        prepareStatementsIfNeeded();
        ResultSet rs = session.execute(takeoverStmt.bind(newOwner, lockId, expectedOwner));
        Row row = rs.one();

        boolean applied = row != null && row.getBoolean(APPLIED_COLUMN);
        if (applied) {
            log.info("Lock {} taken over from {} by {}", lockId, expectedOwner, newOwner);
        } else {
            log.debug("Lock {} takeover failed - owner changed from {}", lockId, expectedOwner);
        }
        return applied;
    }
}
