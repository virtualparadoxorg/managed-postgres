package eu.virtualparadox.managedpostgres.filesystem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Publishes staged directories into final target paths.
 */
public final class DirectoryPublisher {

    /**
     * Creates a directory publisher.
     */
    public DirectoryPublisher() {
    }

    /**
     * Publishes the staging directory into the target path, preferring atomic moves.
     *
     * @param staging staged directory
     * @param target final target directory
     */
    public void publish(final Path staging, final Path target) {
        final Path checkedStaging = Objects.requireNonNull(staging, "staging").toAbsolutePath().normalize();
        final Path checkedTarget = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();

        if (checkedStaging.equals(checkedTarget)) {
            throw new IllegalArgumentException("staging and target must differ");
        }
        if (!Files.isDirectory(checkedStaging)) {
            throw new IllegalArgumentException("staging must be an existing directory: " + checkedStaging);
        }

        if (Files.exists(checkedTarget)) {
            throw new IllegalStateException("target already exists and cannot be replaced crash-safely: " + checkedTarget);
        }

        moveIfAbsent(checkedStaging, checkedTarget);
    }

    /**
     * Performs the move if absent operation.
     *
     * @param source source value
     * @param target target value
     */
    public static void moveIfAbsent(final Path source, final Path target) {
        try {
            Files.createDirectories(parentDirectory(target));
            failIfTargetExists(target);
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException exception) {
            moveNonAtomicallyIfAbsent(source, target);
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to move " + source + " to " + target, exception);
        }
    }

    /**
     * Performs the move replacing existing operation.
     *
     * @param source source value
     * @param target target value
     */
    public static void moveReplacingExisting(final Path source, final Path target) {
        try {
            Files.createDirectories(parentDirectory(target));
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (final AtomicMoveNotSupportedException exception) {
            moveNonAtomically(source, target);
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to move " + source + " to " + target, exception);
        }
    }

    /**
     * Deletes a directory tree when it exists.
     *
     * @param path directory or file tree root to delete
     */
    public static void deleteRecursivelyIfExists(final Path path) {
        if (!Files.exists(path)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(path)) {
            final Iterable<Path> deletionOrder = paths
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (final Path current : deletionOrder) {
                Files.deleteIfExists(current);
            }
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to delete " + path, exception);
        }
    }

    private static void failIfTargetExists(final Path target) throws FileAlreadyExistsException {
        if (Files.exists(target)) {
            throw new FileAlreadyExistsException(target.toString());
        }
    }

    private static Path parentDirectory(final Path target) {
        final Path parent = target.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("target must have a parent directory");
        }

        return parent;
    }

    private static void moveNonAtomically(final Path source, final Path target) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to move " + source + " to " + target, exception);
        }
    }

    private static void moveNonAtomicallyIfAbsent(final Path source, final Path target) {
        try {
            Files.move(source, target);
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to move " + source + " to " + target, exception);
        }
    }

}
