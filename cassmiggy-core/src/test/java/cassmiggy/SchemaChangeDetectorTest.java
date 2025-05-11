package cassmiggy;

import static org.assertj.core.api.Assertions.assertThat;

import cassmiggy.internal.engine.SchemaChangeDetector;
import org.junit.jupiter.api.Test;

class SchemaChangeDetectorTest {

    private final SchemaChangeDetector detector = new SchemaChangeDetector();

    @Test
    void schemaChangingStatementsAreDdl() {
        assertThat(detector.isDdl("CREATE TABLE users (id int PRIMARY KEY)")).isTrue();
        assertThat(detector.isDdl("ALTER TABLE users ADD age int")).isTrue();
        assertThat(detector.isDdl("DROP INDEX users_idx")).isTrue();
        assertThat(detector.isDdl("CREATE KEYSPACE ks WITH replication = {}")).isTrue();
        assertThat(detector.isDdl("  create table leading_whitespace (id int PRIMARY KEY)")).isTrue();
    }

    @Test
    void dmlAndQueriesAreNotDdl() {
        assertThat(detector.isDdl("INSERT INTO users (id) VALUES (1)")).isFalse();
        assertThat(detector.isDdl("UPDATE users SET email = 'x' WHERE id = 1")).isFalse();
        assertThat(detector.isDdl("DELETE FROM users WHERE id = 1")).isFalse();
        assertThat(detector.isDdl("SELECT * FROM users")).isFalse();
        assertThat(detector.isDdl("USE my_keyspace")).isFalse();
        assertThat(detector.isDdl("TRUNCATE users")).isFalse();
        assertThat(detector.isDdl("BEGIN BATCH")).isFalse();
        assertThat(detector.isDdl(null)).isFalse();
        assertThat(detector.isDdl("")).isFalse();
    }
}
