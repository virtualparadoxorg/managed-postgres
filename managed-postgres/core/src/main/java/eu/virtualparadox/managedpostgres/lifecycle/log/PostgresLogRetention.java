package eu.virtualparadox.managedpostgres.lifecycle.log;

import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.filesystem.DirectoryPublisher;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Applies bounded retention to PostgreSQL process log files before startup.
 */
public final class PostgresLogRetention {

    /**
     * Creates a PostgreSQL log retention service.
     */
    public PostgresLogRetention() {
    }

    /**
     * Prepares the process log file according to the configured cleanup policy.
     *
     * @param logFile active PostgreSQL process log path
     * @param cleanupPolicy cleanup and retention policy
     */
    public void prepare(final Path logFile, final CleanupPolicy cleanupPolicy) {
        final Path checkedLogFile = Objects.requireNonNull(logFile, "logFile").toAbsolutePath().normalize();
        final CleanupPolicy checkedPolicy = Objects.requireNonNull(cleanupPolicy, "cleanupPolicy");
        if (shouldRotate(checkedLogFile, checkedPolicy)) {
            rotate(checkedLogFile, checkedPolicy.retainedLogFiles());
        }
    }

    /**
     * Trims rotated PostgreSQL log history without touching the active log file.
     *
     * @param logFile active PostgreSQL process log path
     * @param retainedLogFiles retained rotated log file count
     */
    public void trimHistory(final Path logFile, final int retainedLogFiles) {
        final Path checkedLogFile = Objects.requireNonNull(logFile, "logFile").toAbsolutePath().normalize();
        if (retainedLogFiles < 0) {
            throw new IllegalArgumentException("retainedLogFiles must not be negative");
        }
        deleteRotationsAbove(checkedLogFile, retainedLogFiles);
    }

    private static boolean shouldRotate(final Path logFile, final CleanupPolicy cleanupPolicy) {
        final boolean rotate;
        if (!Files.isRegularFile(logFile)) {
            rotate = false;
        } else {
            rotate = fileSize(logFile) >= cleanupPolicy.rotateLogAboveBytes();
        }

        return rotate;
    }

    private static void rotate(final Path logFile, final int retainedLogFiles) {
        if (retainedLogFiles == 0) {
            deleteLogFamily(logFile);
        } else {
            deleteRotationsAtOrAbove(logFile, retainedLogFiles);
            shiftRotations(logFile, retainedLogFiles);
            DirectoryPublisher.moveReplacingExisting(logFile, rotationPath(logFile, 1));
        }
    }

    private static void shiftRotations(final Path logFile, final int retainedLogFiles) {
        for (int index = retainedLogFiles - 1; index > 0; index--) {
            final Path source = rotationPath(logFile, index);
            if (Files.exists(source)) {
                DirectoryPublisher.moveReplacingExisting(source, rotationPath(logFile, index + 1));
            }
        }
    }

    private static void deleteRotationsAtOrAbove(final Path logFile, final int retainedLogFiles) {
        final List<Integer> indexes = rotationIndexes(logFile);
        final List<Integer> indexesToDelete = indexes.stream()
                .filter(index -> index >= retainedLogFiles)
                .sorted(Comparator.reverseOrder())
                .toList();
        for (final int index : indexesToDelete) {
            deleteIfExists(rotationPath(logFile, index));
        }
    }

    private static void deleteRotationsAbove(final Path logFile, final int retainedLogFiles) {
        final List<Integer> indexes = rotationIndexes(logFile);
        final List<Integer> indexesToDelete = indexes.stream()
                .filter(index -> index > retainedLogFiles)
                .sorted(Comparator.reverseOrder())
                .toList();
        for (final int index : indexesToDelete) {
            deleteIfExists(rotationPath(logFile, index));
        }
    }

    private static void deleteLogFamily(final Path logFile) {
        final List<Integer> indexes = rotationIndexes(logFile);
        for (final int index : indexes) {
            deleteIfExists(rotationPath(logFile, index));
        }
        deleteIfExists(logFile);
    }

    private static List<Integer> rotationIndexes(final Path logFile) {
        final Path parent = parentDirectory(logFile);
        final String fileName = fileName(logFile);
        final List<Integer> indexes;
        if (!Files.isDirectory(parent)) {
            indexes = List.of();
        } else {
            try (Stream<Path> paths = Files.list(parent)) {
                indexes = paths
                        .map(path -> rotationIndex(path, fileName))
                        .flatMap(Optional::stream)
                        .sorted(Comparator.reverseOrder())
                        .toList();
            } catch (final IOException exception) {
                throw new UncheckedIOException("failed to inspect PostgreSQL log directory " + parent, exception);
            }
        }

        return indexes;
    }

    private static Optional<Integer> rotationIndex(final Path path, final String fileName) {
        final String currentFileName = fileName(path);
        final String prefix = fileName + ".";
        final Optional<Integer> index;
        if (currentFileName.startsWith(prefix)) {
            index = parsePositiveInteger(currentFileName.substring(prefix.length()));
        } else {
            index = Optional.empty();
        }

        return index;
    }

    private static Optional<Integer> parsePositiveInteger(final String value) {
        return parseInteger(value).flatMap(PostgresLogRetention::positiveIntegerOrEmpty);
    }

    private static Optional<Integer> parseInteger(final String value) {
        Optional<Integer> integer;
        try {
            integer = Optional.of(Integer.valueOf(value));
        } catch (final NumberFormatException exception) {
            integer = Optional.empty();
        }

        return integer;
    }

    private static Optional<Integer> positiveIntegerOrEmpty(final Integer value) {
        final Optional<Integer> index;
        if (value.intValue() > 0) {
            index = Optional.of(value);
        } else {
            index = Optional.empty();
        }

        return index;
    }

    private static Path rotationPath(final Path logFile, final int index) {
        return logFile.resolveSibling(fileName(logFile) + "." + index);
    }

    private static String fileName(final Path path) {
        return Objects.requireNonNull(path.getFileName(), "fileName").toString();
    }

    private static Path parentDirectory(final Path path) {
        return Objects.requireNonNull(path.getParent(), "parent");
    }

    private static long fileSize(final Path logFile) {
        try {
            return Files.size(logFile);
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to inspect PostgreSQL log file " + logFile, exception);
        }
    }

    private static void deleteIfExists(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to delete PostgreSQL log file " + path, exception);
        }
    }
}
