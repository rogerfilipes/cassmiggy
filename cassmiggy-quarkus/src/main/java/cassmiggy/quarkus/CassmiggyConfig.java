package cassmiggy.quarkus;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import java.time.Duration;
import java.util.List;

/**
 * Configuration for cassmiggy, read from the {@code cassmiggy.*} namespace.
 *
 * <pre>
 * cassmiggy.enabled=true
 * cassmiggy.fail-on-error=true
 * cassmiggy.auto-keyspace=true
 * cassmiggy.lock-timeout=PT5M
 * cassmiggy.schema-agreement-timeout=PT30S
 * cassmiggy.keyspaces[0].keyspace=app
 * cassmiggy.keyspaces[0].location=cql/app
 * cassmiggy.keyspaces[1].keyspace=shared
 * cassmiggy.keyspaces[1].location=cql/shared
 * </pre>
 */
@ConfigMapping(prefix = "cassmiggy")
public interface CassmiggyConfig {

    /** Master enable/disable switch for migrations. */
    @WithDefault("true")
    boolean enabled();

    /** Whether to fail application startup if a migration fails. */
    @WithName("fail-on-error")
    @WithDefault("true")
    boolean failOnError();

    /** Whether the engine may treat the configured keyspace as already selectable. */
    @WithName("auto-keyspace")
    @WithDefault("true")
    boolean autoKeyspace();

    /** Maximum time to wait to acquire the migration lock. */
    @WithName("lock-timeout")
    @WithDefault("PT5M")
    Duration lockTimeout();

    /** Maximum time to wait for schema agreement after DDL. */
    @WithName("schema-agreement-timeout")
    @WithDefault("PT30S")
    Duration schemaAgreementTimeout();

    /** The keyspaces to migrate, in execution order. */
    List<Keyspace> keyspaces();

    /** A single keyspace and the classpath location of its migration files. */
    interface Keyspace {
        String keyspace();

        String location();
    }
}
