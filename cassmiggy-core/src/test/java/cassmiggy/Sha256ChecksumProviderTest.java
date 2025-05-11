package cassmiggy;

import static org.assertj.core.api.Assertions.assertThat;

import cassmiggy.internal.integrity.Sha256ChecksumProvider;
import org.junit.jupiter.api.Test;

class Sha256ChecksumProviderTest {

    private final Sha256ChecksumProvider provider = new Sha256ChecksumProvider();

    @Test
    void producesStable64CharHexChecksum() {
        String checksum = provider.calculateChecksum("CREATE TABLE foo (id int PRIMARY KEY);");

        assertThat(checksum).hasSize(64).matches("[0-9a-f]{64}");
        // Deterministic across calls
        assertThat(provider.calculateChecksum("CREATE TABLE foo (id int PRIMARY KEY);"))
                .isEqualTo(checksum);
    }

    @Test
    void normalizesLineEndingsAndSurroundingWhitespace() {
        String unix = "CREATE TABLE foo (id int PRIMARY KEY);\nSELECT 1;\n";
        String windows = "  CREATE TABLE foo (id int PRIMARY KEY);\r\nSELECT 1;\r\n  ";

        assertThat(provider.calculateChecksum(windows)).isEqualTo(provider.calculateChecksum(unix));
    }

    @Test
    void differentContentProducesDifferentChecksum() {
        assertThat(provider.calculateChecksum("CREATE TABLE a (id int PRIMARY KEY);"))
                .isNotEqualTo(provider.calculateChecksum("CREATE TABLE b (id int PRIMARY KEY);"));
    }

    @Test
    void verifyMatchesCalculatedChecksum() {
        String content = "INSERT INTO foo (id) VALUES (1);";
        String checksum = provider.calculateChecksum(content);

        assertThat(provider.verify(content, checksum)).isTrue();
        assertThat(provider.verify(content, "deadbeef")).isFalse();
    }
}
