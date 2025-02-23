package cassmiggy.internal.engine;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatementBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cassmiggy.exception.StatementExecutionException;

/**
 * Executes individual CQL statements with configurable consistency, optionally pinning a keyspace.
 *
 * For Cassandra 4.0+ (protocol V5) per-request keyspace is used; older versions (V4) fall back
 * to a USE keyspace statement.
 */
public class StatementExecutor {

    private static final Logger log = LoggerFactory.getLogger(StatementExecutor.class);
    private static final int MAX_LOG_LENGTH = 80;

    private final CqlSession session;
    private final DefaultConsistencyLevel writeConsistency;
    private final CqlIdentifier keyspace;
    private final boolean supportsPerRequestKeyspace;
    private boolean keyspaceSet = false;

    public StatementExecutor(CqlSession session, DefaultConsistencyLevel writeConsistency) {
        this(session, writeConsistency, null);
    }

    public StatementExecutor(CqlSession session, DefaultConsistencyLevel writeConsistency, String keyspace) {
        this.session = session;
        this.writeConsistency = writeConsistency;
        this.keyspace = keyspace != null ? CqlIdentifier.fromCql(keyspace) : null;
        this.supportsPerRequestKeyspace = detectPerRequestKeyspaceSupport();

        if (this.keyspace != null && !supportsPerRequestKeyspace) {
            log.debug("Protocol V4 detected - will use USE keyspace for auto-keyspace support");
        }
    }

    private boolean detectPerRequestKeyspaceSupport() {
        ProtocolVersion protocolVersion = session.getContext().getProtocolVersion();
        int code = protocolVersion.getCode();
        boolean supported = code >= 5;
        log.debug( "protocol version {} (code={}), per-request keyspace supported: {}",
                protocolVersion, code, supported);
        return supported;
    }

    private void applyKeyspaceContext() {
        if (keyspace != null
                && !supportsPerRequestKeyspace
                && !keyspaceSet) {
            String useStatement = "USE " + keyspace.asCql(true);
            log.debug("Setting keyspace context with: {}", useStatement);
            session.execute(useStatement);
            keyspaceSet = true;
        }
    }

    /**
     * @param statementIndex 1-based index
     */
    public ResultSet execute(String statement, String migrationFilename, int statementIndex) {
        log.trace("{} - Executing statement {}: {}", migrationFilename, statementIndex, truncate(statement));

        try {
            applyKeyspaceContext();

            SimpleStatementBuilder builder = SimpleStatement.builder(statement).setConsistencyLevel(writeConsistency);

            if (keyspace != null && supportsPerRequestKeyspace) {
                builder.setKeyspace(keyspace);
            }

            return session.execute(builder.build());
        } catch (Exception e) {
            log.error("{} - Statement {} failed: {}", migrationFilename, statementIndex, e.getMessage());
            throw new StatementExecutionException(migrationFilename, statementIndex, statement, e.getMessage(), e);
        }
    }


    public ResultSet execute(String statement) {
        log.debug("Executing statement: {}", truncate(statement));

        try {
            applyKeyspaceContext();

            SimpleStatementBuilder builder = SimpleStatement.builder(statement).setConsistencyLevel(writeConsistency);

            if (keyspace != null && supportsPerRequestKeyspace) {
                builder.setKeyspace(keyspace);
            }

            return session.execute(builder.build());
        } catch (Exception e) {
            log.error("Statement execution failed: {}", e.getMessage());
            throw new RuntimeException("Failed to execute statement: " + e.getMessage(), e);
        }
    }

    private String truncate(String statement) {
        String normalized = statement.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= MAX_LOG_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_LOG_LENGTH - 3) + "...";
    }
}
