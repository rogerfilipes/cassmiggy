package cassmiggy.quarkus;

import cassmiggy.KeyspaceMigrationRunner;
import cassmiggy.KeyspaceMigrationRunner.KeyspaceMigration;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;

/**
 * Runs CQL migrations at Quarkus startup.
 *
 * <p>Observes {@link StartupEvent} with {@code PLATFORM_BEFORE} priority so migrations run before
 * other startup observers that depend on the schema. The application's {@link CqlSession} bean is
 * injected (e.g. provided by the {@code quarkus-cassandra-client} extension or a custom producer).
 */
@ApplicationScoped
public class CassmiggyStartup {

    private static final Logger log = LoggerFactory.getLogger(CassmiggyStartup.class);

    private final CqlSession session;
    private final CassmiggyConfig config;

    @Inject
    public CassmiggyStartup(CqlSession session, CassmiggyConfig config) {
        this.session = session;
        this.config = config;
    }

    void onStart(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE) StartupEvent event) {
        run();
    }

    /**
     * Runs the configured migrations, honoring {@code cassmiggy.fail-on-error}.
     * Exposed (package-private) so it can be exercised directly in tests.
     */
    void run() {
        if (!config.enabled()) {
            log.info("cassmiggy is disabled (cassmiggy.enabled=false)");
            return;
        }
        if (config.keyspaces().isEmpty()) {
            log.warn("cassmiggy is enabled but no keyspaces are configured (cassmiggy.keyspaces)");
            return;
        }

        List<KeyspaceMigration> migrations = config.keyspaces().stream()
                .map(k -> new KeyspaceMigration(k.keyspace(), k.location()))
                .collect(Collectors.toList());

        KeyspaceMigrationRunner runner = KeyspaceMigrationRunner.builder()
                .session(session)
                .autoKeyspace(config.autoKeyspace())
                .lockTimeout(config.lockTimeout())
                .schemaAgreementTimeout(config.schemaAgreementTimeout())
                .build();

        try {
            runner.migrateAll(migrations);
            log.info("cassmiggy completed for {} keyspace(s)", migrations.size());
        } catch (RuntimeException e) {
            if (config.failOnError()) {
                throw e;
            }
            log.error("cassmiggy failed but cassmiggy.fail-on-error=false; continuing", e);
        }
    }
}
