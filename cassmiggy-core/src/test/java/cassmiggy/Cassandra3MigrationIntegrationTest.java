package cassmiggy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.datastax.oss.driver.api.core.CqlSession;

/** Runs the migration scenarios against Cassandra 3.x (native protocol V4 / USE-keyspace path). */
@Testcontainers
class Cassandra3MigrationIntegrationTest extends AbstractMigrationIntegrationTest {

    @Container
    @SuppressWarnings("rawtypes")
    static final CassandraContainer CASSANDRA =
            new CassandraContainer(DockerImageName.parse("cassandra:3.11"));

    static CqlSession session;

    @BeforeAll
    static void openSession() {
        session = CqlSession.builder()
                .addContactPoint(CASSANDRA.getContactPoint())
                .withLocalDatacenter(CASSANDRA.getLocalDatacenter())
                .build();
    }

    @AfterAll
    static void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    @Override
    protected CqlSession session() {
        return session;
    }
}
