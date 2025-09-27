package cassmiggy.examples.plain;

import java.net.InetSocketAddress;

import com.datastax.oss.driver.api.core.CqlSession;

import cassmiggy.Config;
import cassmiggy.model.MigrationResult;
import cassmiggy.SchemaMigrator;

/**
 * Minimal end-to-end example: connect to Cassandra, ensure the target keyspace exists,
 * then apply the bundled {@code cql/app} migrations with the core engine.
 *
 * <p>Run against a local Cassandra (see the module README for a one-line Docker command):
 * <pre>{@code
 *   mvn -pl examples/plain-java-example exec:java
 * }</pre>
 *
 * <p>Connection details can be overridden with the {@code CASSANDRA_HOST}, {@code CASSANDRA_PORT},
 * {@code CASSANDRA_DC} and {@code CASSANDRA_KEYSPACE} environment variables.
 */
public final class PlainJavaExample {

    public static void main(String[] args) {
        String host = env("CASSANDRA_HOST", "127.0.0.1");
        int port = Integer.parseInt(env("CASSANDRA_PORT", "9042"));
        String datacenter = env("CASSANDRA_DC", "datacenter1");
        String keyspace = env("CASSANDRA_KEYSPACE", "app");

        // Connect at the cluster level (no keyspace bound) so we can create the keyspace first.
        try (CqlSession session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress(host, port))
                .withLocalDatacenter(datacenter)
                .build()) {

            // cassmiggy never creates keyspaces, so make sure the target exists before migrating.
            // In production you would manage this with your normal infrastructure tooling.
            session.execute("CREATE KEYSPACE IF NOT EXISTS " + keyspace
                    + " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");

            Config config = Config.builder()
                    .withSession(session)
                    .withKeyspace(keyspace)                // must already exist (created above)
                    .withMigrationsLocation("cql/app")     // classpath location of the .cql files
                    .build();

            MigrationResult result = new SchemaMigrator(config).migrate();

            System.out.printf("%nApplied %d migration(s) in %dms:%n",
                    result.getAppliedCount(), result.getDuration().toMillis());
            result.getAppliedMigrations().forEach(m ->
                    System.out.printf("  - %s (%d statement(s))%n", m.getPath(), m.getStatementCount()));
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private PlainJavaExample() {}
}
