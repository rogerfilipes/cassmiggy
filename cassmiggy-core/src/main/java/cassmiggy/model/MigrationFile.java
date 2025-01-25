package cassmiggy.model;

import java.nio.file.Path;

/** A discovered migration file, from the filesystem or the classpath. */
public final class MigrationFile {
    private final String path;
    private final String filename;
    private final String description;
    private final String checksum;
    private final MigrationFileSource source;

    public MigrationFile(
            String path, String filename, String description, String checksum, MigrationFileSource source) {
        this.path = path;
        this.filename = filename;
        this.description = description;
        this.checksum = checksum;
        this.source = source;
    }

    public String getPath() {
        return path;
    }

    public String getFilename() {
        return filename;
    }

    public String getDescription() {
        return description;
    }

    public String getChecksum() {
        return checksum;
    }

    public MigrationFileSource getSource() {
        return source;
    }

    public sealed interface MigrationFileSource {
        final class FileSystemSource implements MigrationFileSource {
            private final Path path;

            public FileSystemSource(Path path) {
                if (path == null) {
                    throw new IllegalArgumentException("path must not be null");
                }
                this.path = path;
            }

            public Path getPath() {
                return path;
            }
        }

        final class ClasspathSource implements MigrationFileSource {
            private final String resourcePath;

            public ClasspathSource(String resourcePath) {
                if (resourcePath == null || resourcePath.isBlank()) {
                    throw new IllegalArgumentException("resourcePath must not be null or blank");
                }
                this.resourcePath = resourcePath;
            }

            public String getResourcePath() {
                return resourcePath;
            }
        }
    }

    public static MigrationFile fromPath(
            String relativePath, String filename, String description, String checksum, Path filesystemPath) {
        return new MigrationFile(
                relativePath,
                filename,
                description,
                checksum,
                new MigrationFileSource.FileSystemSource(filesystemPath));
    }

    public static MigrationFile fromClasspath(
            String relativePath, String filename, String description, String checksum, String classpathLocation) {
        return new MigrationFile(
                relativePath,
                filename,
                description,
                checksum,
                new MigrationFileSource.ClasspathSource(classpathLocation));
    }

    public boolean isFileSystem() {
        return source instanceof MigrationFileSource.FileSystemSource;
    }

    public boolean isClasspath() {
        return source instanceof MigrationFileSource.ClasspathSource;
    }
}
