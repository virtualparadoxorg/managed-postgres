package eu.virtualparadox.managedpostgres.filesystem.lock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Managed filesystem boundary for lifecycle lock files.
 */
public final class ManagedLockFiles {

    /**
     * Creates a managed lock-file adapter.
     */
    public ManagedLockFiles() {}

    /**
     * Opens a lifecycle lock file, creating its parent directory and the lock file when needed.
     *
     * @param lockFile lock file path
     * @return opened lock file channel
     */
    public FileChannel open(final Path lockFile) {
        final Path normalizedLockFile =
                Objects.requireNonNull(lockFile, "lockFile").toAbsolutePath().normalize();

        try {
            Files.createDirectories(parentDirectory(normalizedLockFile));
            return FileChannel.open(normalizedLockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to open lifecycle lock file " + normalizedLockFile, exception);
        }
    }

    private static Path parentDirectory(final Path path) {
        final Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("lockFile must have a parent directory");
        }

        return parent;
    }
}
