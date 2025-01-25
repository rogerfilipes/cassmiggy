package cassmiggy.internal.integrity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cassmiggy.exception.IntegrityException;
import cassmiggy.spi.ChecksumProvider;

/**
 * SHA-256 checksum provider. Normalizes content before hashing so checksums are
 * consistent across platforms (line endings, surrounding whitespace).
 */
public class Sha256ChecksumProvider implements ChecksumProvider {

    private static final Logger log = LoggerFactory.getLogger(Sha256ChecksumProvider.class);
    private static final String ALGORITHM = "SHA-256";
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    @Override
    public String calculateChecksum(Path file) {
        log.debug("Calculating checksum for file: {}", file);
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return calculateChecksum(content);
        } catch (IOException e) {
            throw new IntegrityException(file, "Failed to read file for checksum calculation", e);
        }
    }

    @Override
    public String calculateChecksum(String content) {
        if (content == null) {
            content = "";
        }

        String normalized = normalize(content);

        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));

            String checksum = bytesToHex(hash);
            log.debug("Calculated checksum: {}", checksum);
            return checksum;
        } catch (NoSuchAlgorithmException e) {
            throw new IntegrityException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public boolean verify(Path file, String expectedChecksum) {
        log.debug("Verifying checksum for file: {}", file);
        String actualChecksum = calculateChecksum(file);
        boolean match = actualChecksum.equalsIgnoreCase(expectedChecksum);
        if (!match) {
            log.warn("Checksum mismatch for {}. Expected: {}, Actual: {}", file, expectedChecksum, actualChecksum);
        }
        return match;
    }

    @Override
    public boolean verify(String content, String expectedChecksum) {
        String actualChecksum = calculateChecksum(content);
        boolean match = actualChecksum.equalsIgnoreCase(expectedChecksum);
        log.debug("Content checksum verification: {}", match ? "passed" : "failed");
        return match;
    }

    private String normalize(String content) {
        return content.replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    // https://stackoverflow.com/a/9855338
    private String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }
}
