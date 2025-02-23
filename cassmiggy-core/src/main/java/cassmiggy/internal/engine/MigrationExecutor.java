package cassmiggy.internal.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cassmiggy.Config;
import cassmiggy.model.MigrationFile;
import cassmiggy.exception.InvalidMigrationException;
import cassmiggy.exception.MigrationException;
import cassmiggy.exception.SchemaAgreementException;
import cassmiggy.spi.MigrationDiscovery;
import cassmiggy.spi.MigrationParser;
import cassmiggy.spi.SchemaAgreementChecker;

public class MigrationExecutor {

    private static final Logger log = LoggerFactory.getLogger(MigrationExecutor.class);

    private final Config config;
    private final MigrationDiscovery discovery;
    private final MigrationParser parser;
    private final StatementExecutor statementExecutor;
    private final SchemaChangeDetector ddlDetector;
    private final SchemaAgreementChecker agreementChecker;

    public MigrationExecutor(
            Config config,
            MigrationDiscovery discovery,
            MigrationParser parser,
            StatementExecutor statementExecutor,
            SchemaAgreementChecker agreementChecker) {
        this.config = config;
        this.discovery = discovery;
        this.parser = parser;
        this.statementExecutor = statementExecutor;
        this.ddlDetector = new SchemaChangeDetector();
        this.agreementChecker = agreementChecker;
    }

    public ExecutionResult execute(MigrationFile migration) {
        String filename = migration.getFilename();
        log.info("Executing migration {}: {}", filename, migration.getDescription());

        Instant startTime = Instant.now();
        String content = discovery.getContent(migration);
        List<String> statements = parser.parse(content);

        if (statements.isEmpty()) {
            throw new InvalidMigrationException(
                    migration.getFilename(),
                    "Migration file is empty or contains only comments. ");
        }

        log.debug("{}: Parsed {} statements", filename, statements.size());

        int executedCount = 0;

        for (int i = 0; i < statements.size(); i++) {
            String stmt = statements.get(i);
            int stmtNum = i + 1;

            log.trace("{} - Statement {}/{}", filename, stmtNum, statements.size());

            try {
                statementExecutor.execute(stmt, filename, stmtNum);
                executedCount++;

                boolean isDdl = ddlDetector.isDdl(stmt);

                // Always wait for schema agreement after a DDL statement.
                if (isDdl || !config.isSchemaAgreementOnDdlOnly()) {
                    waitForSchemaAgreement(filename, stmtNum);
                }
            } catch (MigrationException e) {
                throw new MigrationExecutionException(filename, executedCount, e);
            }
        }

        Duration executionTime = Duration.between(startTime, Instant.now());

        log.info("{}: Completed {} statements in {}ms", filename, executedCount, executionTime.toMillis());

        return new ExecutionResult(filename, executedCount, executionTime, true);
    }

    private void waitForSchemaAgreement(String filename, int statementIndex) {
        log.trace("{}: Waiting for schema agreement after statement {}", filename, statementIndex);

        boolean agreed = agreementChecker.waitForAgreement(
                config.getSchemaAgreementTimeout(), config.getSchemaAgreementPollInterval());

        if (!agreed) {
            throw new SchemaAgreementException(filename, statementIndex, config.getSchemaAgreementTimeout());
        }

        log.trace("{}: Schema agreement reached", filename);
    }
}
