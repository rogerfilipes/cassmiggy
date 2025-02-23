package cassmiggy.spi;

import java.time.Duration;

/**
 * Contract for verifying schema agreement across a Cassandra cluster.
 *
 * <p>Schema agreement means all nodes report the same schema version UUID.
 * After a DDL statement (CREATE TABLE, ALTER, DROP, etc.), the schema change
 * propagates asynchronously to other nodes. Executing statements against
 * a node that hasn't received the schema update will fail.
 *
 * <p>This checker polls until all nodes agree or a timeout is reached.
 *
 * @see cassmiggy.internal.schema.DriverSchemaAgreementChecker
 */
public interface SchemaAgreementChecker {

    /**
     * @return true if agreement reached, false if timeout expired
     */
    boolean waitForAgreement(Duration timeout, Duration pollInterval);

    boolean isInAgreement();
}
