package eu.virtualparadox.managedpostgres.runtime.download;

import eu.virtualparadox.managedpostgres.filesystem.DirectoryPublisher;
import eu.virtualparadox.managedpostgres.filesystem.ManagedPathOwnership;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Cleans framework-owned incomplete runtime cache paths.
 */
public final class RuntimeCacheCleaner {

    private static final String PARTIAL_DOWNLOAD_SUFFIX = ".download";
    private static final String STAGING_SUFFIX = ".staging";

    private final ManagedPathOwnership ownership;

    /**
     * Creates a runtime cache cleaner with default ownership marker handling.
     */
    public RuntimeCacheCleaner() {
        this(new ManagedPathOwnership());
    }

    private RuntimeCacheCleaner(final ManagedPathOwnership ownership) {
        this.ownership = Objects.requireNonNull(ownership, "ownership");
    }

    /**
     * Deletes stale framework-owned partial downloads and owned staging directories.
     *
     * @param layout runtime cache layout to clean
     */
    public void clean(final RuntimeCacheLayout layout) {
        final RuntimeCacheLayout checkedLayout = Objects.requireNonNull(layout, "layout");

        cleanDownloads(checkedLayout.downloadsDirectory());
        cleanRuntimes(checkedLayout.runtimesDirectory());
    }

    private void cleanDownloads(final Path downloadsDirectory) {
        if (!Files.isDirectory(downloadsDirectory)) {
            return;
        }

        try (Stream<Path> paths = Files.list(downloadsDirectory)) {
            deletePartialDownloads(paths.toList());
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to clean runtime downloads in " + downloadsDirectory, exception);
        }
    }

    private void cleanRuntimes(final Path runtimesDirectory) {
        if (!Files.isDirectory(runtimesDirectory)) {
            return;
        }

        try (Stream<Path> paths = Files.list(runtimesDirectory)) {
            deleteOwnedStagingDirectories(paths.toList());
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to clean runtime staging paths in " + runtimesDirectory, exception);
        }
    }

    private static void deletePartialDownloads(final Iterable<Path> paths) {
        for (final Path path : paths) {
            if (isPartialDownload(path)) {
                deleteFile(path);
            }
        }
    }

    private void deleteOwnedStagingDirectories(final Iterable<Path> paths) {
        for (final Path path : paths) {
            if (isStagingDirectory(path) && ownership.isOwned(path)) {
                DirectoryPublisher.deleteRecursivelyIfExists(path);
            }
        }
    }

    private static boolean isPartialDownload(final Path path) {
        final Path fileName = path.getFileName();
        return fileName != null
                && Files.isRegularFile(path)
                && fileName.toString().endsWith(PARTIAL_DOWNLOAD_SUFFIX);
    }

    private static boolean isStagingDirectory(final Path path) {
        final Path fileName = path.getFileName();
        return fileName != null
                && Files.isDirectory(path)
                && fileName.toString().endsWith(STAGING_SUFFIX);
    }

    private static void deleteFile(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to delete runtime cache file " + path, exception);
        }
    }
}
