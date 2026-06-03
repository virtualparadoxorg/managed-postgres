package eu.virtualparadox.managedpostgres.runtime.packaging.build.execution;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Parses Meson's "conflicting files" setup failure and clears the in-tree files it lists.
 *
 * <p>Some PostgreSQL release tarballs (e.g. 16.x) ship pre-generated in-tree files that Meson
 * refuses to build over. Meson lists them; this resolver removes exactly those files (only when
 * they live inside the source tree) so that {@code meson setup} can be retried.
 */
final class MesonSetupConflictResolver {

    private static final String CONFLICT_MARKER = "Conflicting files in source directory:";
    private static final String CONFLICT_FOOTER = "The conflicting files need to be removed";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private MesonSetupConflictResolver() {
    }

    /**
     * Returns the in-tree files reported as conflicting by a Meson setup failure message.
     *
     * @param message Meson setup failure message (may be empty)
     * @param sourceTree PostgreSQL source tree
     * @return conflicting files inside the source tree, or an empty list when none apply
     */
    static List<Path> conflictingFiles(final String message, final Path sourceTree) {
        final List<Path> conflicts = new ArrayList<>();
        if (message.contains(CONFLICT_MARKER)) {
            final int start = message.indexOf(CONFLICT_MARKER) + CONFLICT_MARKER.length();
            final int footer = message.indexOf(CONFLICT_FOOTER, start);
            final String region = footer < 0 ? message.substring(start) : message.substring(start, footer);
            final Path normalizedSource = sourceTree.toAbsolutePath().normalize();
            for (final String token : WHITESPACE.split(region, -1)) {
                if (!token.isEmpty()) {
                    final Path candidate = Path.of(token).toAbsolutePath().normalize();
                    if (candidate.startsWith(normalizedSource) && Files.isRegularFile(candidate)) {
                        conflicts.add(candidate);
                    }
                }
            }
        }
        return conflicts;
    }

    /**
     * Removes the supplied conflicting files and wipes the partial Meson build directory.
     *
     * @param conflicts conflicting files to delete
     * @param mesonBuildDirectory partial Meson build directory to wipe
     */
    static void clear(final List<Path> conflicts, final Path mesonBuildDirectory) {
        for (final Path file : conflicts) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException exception) {
                throw new UncheckedIOException("failed to remove conflicting source file: " + file, exception);
            }
        }
        wipeDirectory(mesonBuildDirectory);
    }

    private static void wipeDirectory(final Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(MesonSetupConflictResolver::deleteQuietly);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to wipe meson build directory: " + directory, exception);
        }
    }

    private static void deleteQuietly(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to delete build directory entry: " + path, exception);
        }
    }
}
