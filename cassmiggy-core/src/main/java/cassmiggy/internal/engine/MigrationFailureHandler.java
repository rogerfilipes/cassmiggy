package cassmiggy.internal.engine;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cassmiggy.model.MigrationFile;
import cassmiggy.exception.SchemaAgreementException;
import cassmiggy.exception.StatementExecutionException;
import cassmiggy.spi.MigrationRepository;

public class MigrationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(MigrationFailureHandler.class);

    private final MigrationRepository repository;

    public MigrationFailureHandler(MigrationRepository repository) {
        this.repository = repository;
    }

    public void handleFailure(MigrationFile migration, Instant startTime, int executedStatements, Exception exception) {
        long executionMs = Duration.between(startTime, Instant.now()).toMillis();
        String errorMessage = extractErrorMessage(exception);

        log.error("Migration {} failed after {} statements: {}", migration.getPath(), executedStatements, errorMessage);

        repository.recordFailure(
                migration.getPath(),
                migration.getFilename(),
                migration.getDescription(),
                migration.getChecksum(),
                executionMs,
                executedStatements,
                errorMessage);
    }

    private String extractErrorMessage(Exception exception) {
        if (exception instanceof StatementExecutionException e) {
            return String.format("Statement %d failed: %s", e.getStatementIndex(), e.getMessage());
        } else if (exception instanceof SchemaAgreementException e) {
            return String.format("Schema agreement failed after statement %d", e.getStatementIndex());
        }
        return exception.getMessage();
    }
}
