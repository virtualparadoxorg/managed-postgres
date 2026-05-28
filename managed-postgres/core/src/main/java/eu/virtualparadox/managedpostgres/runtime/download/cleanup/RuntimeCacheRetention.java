package eu.virtualparadox.managedpostgres.runtime.download.cleanup;

import eu.virtualparadox.managedpostgres.filesystem.DirectoryPublisher;
import eu.virtualparadox.managedpostgres.filesystem.ManagedPathOwnership;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheLayout;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Applies count-based retention to framework-owned final runtime cache directories.
 */
public final class RuntimeCacheRetention {

    private static final String STAGING_SUFFIX = ".staging";

    private final ManagedPathOwnership ownership;

    /**
     * Creates runtime cache retention with default ownership marker handling.
     */
    public RuntimeCacheRetention() {
        this(new ManagedPathOwnership());
    }

    private RuntimeCacheRetention(final ManagedPathOwnership ownership) {
        this.ownership = Objects.requireNonNull(ownership, "ownership");
    }

    /**
     * Retains the current runtime and newest owned runtime directories up to the configured count.
     *
     * @param layout runtime cache layout
     * @param currentRuntime current runtime directory
     * @param retainedRuntimeVersions retained runtime version count
     */
    public void retain(
            final RuntimeCacheLayout layout,
            final Path currentRuntime,
            final int retainedRuntimeVersions) {
        requirePositive(retainedRuntimeVersions);
        final RuntimeCacheLayout checkedLayout = Objects.requireNonNull(layout, "layout");
        final Path checkedCurrentRuntime = normalize(currentRuntime);
        final List<RuntimeEntry> entries = ownedFinalRuntimeEntries(checkedLayout);
        final Set<Path> retainedPaths = retainedPaths(entries, checkedCurrentRuntime, retainedRuntimeVersions);

        deleteUnretained(entries, retainedPaths);
    }

    private List<RuntimeEntry> ownedFinalRuntimeEntries(final RuntimeCacheLayout layout) {
        final List<RuntimeEntry> entries;
        if (!Files.isDirectory(layout.runtimesDirectory())) {
            entries = List.of();
        } else {
            try (Stream<Path> paths = Files.list(layout.runtimesDirectory())) {
                entries = paths
                        .map(RuntimeCacheRetention::normalize)
                        .filter(this::isOwnedFinalRuntimeDirectory)
                        .map(RuntimeCacheRetention::entry)
                        .sorted(RuntimeEntry.newestFirst())
                        .toList();
            } catch (final IOException exception) {
                throw new UncheckedIOException(
                        "failed to inspect runtime cache directory " + layout.runtimesDirectory(),
                        exception);
            }
        }

        return entries;
    }

    private boolean isOwnedFinalRuntimeDirectory(final Path path) {
        return Files.isDirectory(path) && !isStaging(path) && ownership.isOwned(path);
    }

    private static Set<Path> retainedPaths(
            final List<RuntimeEntry> entries,
            final Path currentRuntime,
            final int retainedRuntimeVersions) {
        final Set<Path> retainedPaths = new HashSet<>();
        if (entries.stream().map(RuntimeEntry::path).anyMatch(currentRuntime::equals)) {
            retainedPaths.add(currentRuntime);
        }
        for (final RuntimeEntry entry : entries) {
            if (retainedPaths.size() < retainedRuntimeVersions) {
                retainedPaths.add(entry.path());
            }
        }

        return retainedPaths;
    }

    private static void deleteUnretained(final List<RuntimeEntry> entries, final Set<Path> retainedPaths) {
        for (final RuntimeEntry entry : entries) {
            if (!retainedPaths.contains(entry.path())) {
                DirectoryPublisher.deleteRecursivelyIfExists(entry.path());
            }
        }
    }

    private static boolean isStaging(final Path path) {
        return fileName(path).endsWith(STAGING_SUFFIX);
    }

    private static RuntimeEntry entry(final Path path) {
        return new RuntimeEntry(path, lastModifiedTime(path));
    }

    private static FileTime lastModifiedTime(final Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (final IOException exception) {
            throw new UncheckedIOException("failed to inspect runtime cache entry " + path, exception);
        }
    }

    private static Path normalize(final Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    private static String fileName(final Path path) {
        return Objects.requireNonNull(path.getFileName(), "fileName").toString();
    }

    private static void requirePositive(final int retainedRuntimeVersions) {
        if (retainedRuntimeVersions <= 0) {
            throw new IllegalArgumentException("retainedRuntimeVersions must be positive");
        }
    }

    private record RuntimeEntry(Path path, FileTime modifiedTime) {

        private RuntimeEntry {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(modifiedTime, "modifiedTime");
        }

        private static Comparator<RuntimeEntry> newestFirst() {
            return Comparator
                    .comparing(RuntimeEntry::modifiedTime)
                    .reversed()
                    .thenComparing(RuntimeEntry::path);
        }
    }
}
