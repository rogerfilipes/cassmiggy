package cassmiggy.internal.schema;

import java.time.Duration;
import java.time.Instant;

import com.datastax.oss.driver.api.core.CqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cassmiggy.spi.SchemaAgreementChecker;

/**
 * Schema agreement checker using the DataStax Java driver. Calls
 * {@link CqlSession#checkSchemaAgreement()}, which queries each node's schema version and
 * returns true when all versions match, polling until agreement or timeout.
 */
public class DriverSchemaAgreementChecker implements SchemaAgreementChecker {

    private static final Logger log = LoggerFactory.getLogger(DriverSchemaAgreementChecker.class);

    private final CqlSession session;

    public DriverSchemaAgreementChecker(CqlSession session) {
        this.session = session;
    }

    @Override
    public boolean waitForAgreement(Duration timeout, Duration pollInterval) {
        log.debug("Waiting for schema agreement (timeout: {}, poll interval: {})", timeout, pollInterval);

        Instant deadline = Instant.now().plus(timeout);
        int attempt = 0;

        while (Instant.now().isBefore(deadline)) {
            attempt++;

            if (isInAgreement()) {
                log.debug("Schema agreement reached after {} attempt(s)", attempt);
                return true;
            }

            log.trace("Waiting for schema agreement (attempt {})", attempt);

            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Schema agreement wait interrupted");
                return false;
            }
        }

        log.error("Schema agreement not reached after {} attempts (timeout: {})", attempt, timeout);
        return false;
    }

    @Override
    public boolean isInAgreement() {
        try {
            boolean inAgreement = session.checkSchemaAgreement();
            log.trace("Schema agreement check: {}", inAgreement);
            return inAgreement;
        } catch (Exception e) {
            log.warn("Error checking schema agreement: {}", e.getMessage());
            return false;
        }
    }
}
