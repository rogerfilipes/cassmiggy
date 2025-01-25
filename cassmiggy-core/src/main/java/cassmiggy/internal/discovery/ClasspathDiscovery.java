package cassmiggy.internal.discovery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
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
 * Discovers .cql files on the classpath, sorted alphabetically by relative path.
 *
 */
public class ClasspathDiscovery implements MigrationDiscovery {

    private static final Logger log = LoggerFactory.getLogger(ClasspathDiscovery.class);
    private static final String CQL_EXTENSION = ".cql";

    private final String classpathLocation;
    private final ChecksumProvider checksumProvider;
    private final ClassLoader classLoader;

    public ClasspathDiscovery(String classpathLocation, ChecksumProvider checksumProvider) {
        this(classpathLocation, checksumProvider, Thread.currentThread().getContextClassLoader());
    }

    public ClasspathDiscovery(String classpathLocation, ChecksumProvider checksumProvider, ClassLoader classLoader) {
        this.classpathLocation = classpathLocation.startsWith("/") ? classpathLocation.substring(1) : classpathLocation;
        this.checksumProvider = checksumProvider;
        this.classLoader = classLoader;
    }

    @Override
    public List<MigrationFile> discover() {
        log.trace("Discovering migrations from classpath: {}", classpathLocation);

        List<MigrationFile> migrations = new ArrayList<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(classpathLocation);
            while (resources.hasMoreElements()) {
                migrations.addAll(scanResource(resources.nextElement()));
            }
        } catch (IOException e) {
            throw new DiscoveryException(classpathLocation, "Failed to scan classpath", e);
        }

        checkNoDuplicates(migrations);
        migrations.sort(Comparator.comparing(MigrationFile::getPath));

        log.trace("Discovered {} migration files from classpath", migrations.size());
        return migrations;
    }

    private List<MigrationFile> scanResource(URL resourceUrl) throws IOException {
        String protocol = resourceUrl.getProtocol();

        if ("file".equals(protocol)) {
            return scanFileSystem(resourceUrl);
        } else if ("jar".equals(protocol)) {
            return scanJar(resourceUrl);
        }
        log.warn("Unsupported classpath: {}", protocol);
        return List.of();
    }

    private List<MigrationFile> scanFileSystem(URL resourceUrl) throws IOException {
        try {
            Path basePath = Path.of(resourceUrl.toURI());
            try (Stream<Path> paths = Files.walk(basePath)) {
                return paths.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(CQL_EXTENSION))
                        .sorted()
                        .map(p -> {
                            String relativePath = basePath.relativize(p).toString().replace('\\', '/');
                            return toMigrationFile(p.getFileName().toString(), relativePath);
                        })
                        .toList();
            }
        } catch (URISyntaxException e) {
            throw new IOException("Invalid resource URI: " + resourceUrl, e);
        }
    }

    private List<MigrationFile> scanJar(URL resourceUrl) throws IOException {
        String jarPath = resourceUrl.getPath();
        int separator = jarPath.indexOf("!");
        if (separator == -1) {
            return List.of();
        }

        String jarFile = jarPath.substring(0, separator);
        try {
            URI jarUri = new URI("jar:" + jarFile);
            try (FileSystem fs = FileSystems.newFileSystem(jarUri, Collections.emptyMap())) {
                Path jarRoot = fs.getPath("/" + classpathLocation);
                if (!Files.exists(jarRoot)) {
                    return List.of();
                }
                try (Stream<Path> paths = Files.walk(jarRoot)) {
                    return paths.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().toLowerCase().endsWith(CQL_EXTENSION))
                            .sorted()
                            .map(p -> {
                                String relativePath = jarRoot.relativize(p).toString().replace('\\', '/');
                                return toMigrationFile(p.getFileName().toString(), relativePath);
                            })
                            .toList();
                }
            }
        } catch (URISyntaxException e) {
            throw new IOException("Invalid JAR URI: " + jarFile, e);
        }
    }

    private MigrationFile toMigrationFile(String filename, String relativePath) {
        String resourcePath = classpathLocation + "/" + relativePath;
        String description = extractDescription(filename);
        String checksum = checksumProvider.calculateChecksum(getContentFromClasspath(resourcePath));
        log.trace("Discovered migration: {} (classpath)", relativePath);
        return MigrationFile.fromClasspath(relativePath, filename, description, checksum, resourcePath);
    }

    private void checkNoDuplicates(List<MigrationFile> migrations) {
        Set<String> seen = new HashSet<>();
        for (MigrationFile migration : migrations) {
            if (!seen.add(migration.getPath().toLowerCase())) {
                throw new DuplicateVersionException(
                        migration.getPath(), "Duplicate migration path: " + migration.getPath());
            }
        }
    }

    private String extractDescription(String filename) {
        String name = filename.substring(0, filename.length() - CQL_EXTENSION.length());
        return name.replace("_", " ");
    }

    @Override
    public String getContent(MigrationFile migration) {
        if (!(migration.getSource() instanceof MigrationFile.MigrationFileSource.ClasspathSource cp)) {
            throw new IllegalArgumentException("Migration has no classpath location");
        }
        return getContentFromClasspath(cp.getResourcePath());
    }

    private String getContentFromClasspath(String resourcePath) {
        try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new DiscoveryException("Resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        } catch (IOException e) {
            throw new DiscoveryException(resourcePath, "Failed to read classpath resource", e);
        }
    }
}
