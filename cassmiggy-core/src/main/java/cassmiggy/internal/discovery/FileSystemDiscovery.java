package cassmiggy.internal.discovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cassmiggy.model.MigrationFile;
import cassmiggy.exception.DiscoveryException;
import cassmiggy.exception.DuplicateVersionException;
import cassmiggy.spi.ChecksumProvider;
import cassmiggy.spi.MigrationDiscovery;

/**
 * Discovers .cql files under a directory, sorted alphabetically
 */
public class FileSystemDiscovery implements MigrationDiscovery {

    private static final Logger log = LoggerFactory.getLogger(FileSystemDiscovery.class);
    private static final String CQL_EXTENSION = ".cql";

    private final Path migrationsPath;
    private final ChecksumProvider checksumProvider;

    public FileSystemDiscovery(Path migrationsPath, ChecksumProvider checksumProvider) {
        this.migrationsPath = migrationsPath;
        this.checksumProvider = checksumProvider;
    }

    @Override
    public List<MigrationFile> discover() {
        log.info("Discovering migrations from path: {}", migrationsPath);

        if (!Files.exists(migrationsPath)) {
            log.warn("Migrations path does not exist: {}", migrationsPath);
            return List.of();
        }

        List<MigrationFile> migrations;
        try (Stream<Path> paths = Files.walk(migrationsPath)) {
            migrations = paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(CQL_EXTENSION))
                    .sorted()
                    .map(this::toMigrationFile)
                    .toList();
        } catch (IOException e) {
            throw new DiscoveryException(migrationsPath.toString(), "Failed to scan migrations directory", e);
        }

        checkNoDuplicates(migrations);
        log.info("Discovered {} migration files", migrations.size());
        return migrations;
    }

    private MigrationFile toMigrationFile(Path path) {
        String relativePath = migrationsPath.relativize(path).toString().replace('\\', '/');
        String filename = path.getFileName().toString();
        String description = extractDescription(filename);
        String checksum = checksumProvider.calculateChecksum(path);
        log.trace("Discovered migration: {}", relativePath);
        return MigrationFile.fromPath(relativePath, filename, description, checksum, path);
    }

    private void checkNoDuplicates(List<MigrationFile> migrations) {
        Set<String> seen = new HashSet<>();
        for (MigrationFile migration : migrations) {
            if (!seen.add(migration.getPath().toLowerCase())) {
                throw new DuplicateVersionException(
                        migration.getPath(),
                        "Duplicate migration path detected (case-insensitive): " + migration.getPath());
            }
        }
    }

    private String extractDescription(String filename) {
        String name = filename.substring(0, filename.length() - CQL_EXTENSION.length());
        return name.replace("_", " ");
    }

    @Override
    public String getContent(MigrationFile migration) {
        if (!(migration.getSource() instanceof MigrationFile.MigrationFileSource.FileSystemSource fs)) {
            throw new DiscoveryException("Migration " + migration.getPath() + " has no file path");
        }
        try {
            return Files.readString(fs.getPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DiscoveryException(fs.getPath().toString(), "Failed to read migration file", e);
        }
    }
}
