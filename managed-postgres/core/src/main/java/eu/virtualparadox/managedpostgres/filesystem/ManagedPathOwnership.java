package eu.virtualparadox.managedpostgres.filesystem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;

/**
 * Writes and reads managed ownership markers for staging paths.
 */
public final class ManagedPathOwnership {

    private static final String MARKER_FILE_NAME = ".managed-postgres-owner";

    /**
     * Creates ownership marker support.
     */
    public ManagedPathOwnership() {
    }

    /**
     * Resolves the ownership marker path for a staging directory.
     *
     * @param staging staging directory
     * @return ownership marker path
     */
    public Path markerPath(final Path staging) {
        return Objects.requireNonNull(staging, "staging").resolve(MARKER_FILE_NAME);
    }

    /**
     * Writes a managed ownership marker into a staging directory.
     *
     * @param staging staging directory
     * @param operationName operation that created the staging directory
     */
    public void writeMarker(final Path staging, final String operationName) {
        final Path markerPath = markerPath(staging);
        final String checkedOperationName = Objects.requireNonNull(operationName, "operationName");
        final String content = "owner=managed-postgres%noperation=%s%ncreatedAt=%s%n"
                .formatted(checkedOperationName, Instant.now());

        try {
            Files.createDirectories(parentDirectory(markerPath));
            Files.writeString(
                    markerPath,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to write ownership marker " + markerPath, exception);
        }
    }

    /**
     * Reports whether a staging directory has a managed ownership marker.
     *
     * @param staging staging directory
     * @return true when the staging directory is owned by managed-postgres
     */
    public boolean isOwned(final Path staging) {
        return Files.isRegularFile(markerPath(staging));
    }

    private static Path parentDirectory(final Path path) {
        final Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("path must have a parent directory");
        }

        return parent;
    }
}
