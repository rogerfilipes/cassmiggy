package cassmiggy.spi;

import java.nio.file.Path;

/**
 * Provides checksum calculation and verification for migration files.
 */
public interface ChecksumProvider {


    String calculateChecksum(Path file);


    String calculateChecksum(String content);


    boolean verify(Path file, String expectedChecksum);


    boolean verify(String content, String expectedChecksum);
}
