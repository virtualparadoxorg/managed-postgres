package eu.virtualparadox.managedpostgres.runtime.packaging.bundle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes normalized runtime bundles into archive files.
 */
public final class BundleArchiveWriter {

    /**
     * Creates a bundle archive writer.
     */
    public BundleArchiveWriter() {}

    /**
     * Writes a ZIP archive from a normalized bundle directory.
     *
     * @param normalizedBundle normalized bundle directory
     * @param archivePath target archive path
     * @return written archive path
     */
    public Path write(final Path normalizedBundle, final Path archivePath) {
        final Path validatedNormalizedBundle = Objects.requireNonNull(normalizedBundle, "normalizedBundle");
        final Path validatedArchivePath = Objects.requireNonNull(archivePath, "archivePath");
        try {
            createParentDirectories(validatedArchivePath);
            try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(validatedArchivePath))) {
                try (Stream<Path> bundleEntries = Files.walk(validatedNormalizedBundle)) {
                    bundleEntries
                            .filter(path -> !Files.isDirectory(path))
                            .forEach(path -> writeEntry(validatedNormalizedBundle, path, outputStream));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to write runtime bundle archive", exception);
        }

        return validatedArchivePath;
    }

    private static void writeEntry(final Path root, final Path path, final ZipOutputStream outputStream) {
        final ZipEntry entry = new ZipEntry(root.relativize(path).toString());
        try {
            outputStream.putNextEntry(entry);
            Files.copy(path, outputStream);
            outputStream.closeEntry();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to add bundle entry " + requireFileName(path), exception);
        }
    }

    private static void createParentDirectories(final Path archivePath) throws IOException {
        final Path parent = archivePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static Path requireFileName(final Path path) {
        return Objects.requireNonNull(path.getFileName(), "path.fileName");
    }
}
