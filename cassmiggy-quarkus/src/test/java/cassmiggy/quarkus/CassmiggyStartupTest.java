package cassmiggy.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.datastax.oss.driver.api.core.CqlSession;

/**
 * Exercises the Quarkus startup bean's migration logic directly, with a hand-built
 * {@link CassmiggyConfig} implementation, against a real Cassandra container. This avoids
 * bootstrapping the full Quarkus runtime while still validating the bean's behavior.
 */
@Testcontainers
class CassmiggyStartupTest {

    @Container
    @SuppressWarnings("rawtypes")
    static final CassandraContainer CASSANDRA =
            new CassandraContainer(DockerImageName.parse("cassandra:4.1"));

    static final String KEYSPACE = "quarkus_it";
    static CqlSession session;

    @BeforeAll
    static void setUp() {
        session = CqlSession.builder()
                .addContactPoint(CASSANDRA.getContactPoint())
                .withLocalDatacenter(CASSANDRA.getLocalDatacenter())
                .build();
        session.execute("CREATE KEYSPACE IF NOT EXISTS " + KEYSPACE
                + " WITH replication = {'class':'SimpleStrategy','replication_factor':1}");
    }

    @AfterAll
    static void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void runsConfiguredMigrations() {
        CassmiggyConfig config = new TestConfig(true, List.of(keyspace(KEYSPACE, "migrations")));

        new CassmiggyStartup(session, config).run();

        assertThat(session.getMetadata()
                        .getKeyspace(KEYSPACE)
                        .orElseThrow()
                        .getTable("gadgets"))
                .isPresent();
    }

    @Test
    void doesNothingWhenDisabled() {
        CassmiggyConfig config = new TestConfig(false, List.of(keyspace("never", "migrations")));

        // Should not throw and should not attempt to migrate the (non-existent) keyspace.
        new CassmiggyStartup(session, config).run();
    }

    private static CassmiggyConfig.Keyspace keyspace(String keyspace, String location) {
        return new CassmiggyConfig.Keyspace() {
            @Override
            public String keyspace() {
                return keyspace;
            }

            @Override
            public String location() {
                return location;
            }
        };
    }

    /** Minimal hand-rolled implementation of the {@code @ConfigMapping} interface for tests. */
    private record TestConfig(boolean enabled, List<CassmiggyConfig.Keyspace> keyspaces)
            implements CassmiggyConfig {
        @Override
        public boolean failOnError() {
            return true;
        }

        @Override
        public boolean autoKeyspace() {
            return true;
        }

        @Override
        public Duration lockTimeout() {
            return Duration.ofMinutes(5);
        }

        @Override
        public Duration schemaAgreementTimeout() {
            return Duration.ofSeconds(30);
        }
    }
}
