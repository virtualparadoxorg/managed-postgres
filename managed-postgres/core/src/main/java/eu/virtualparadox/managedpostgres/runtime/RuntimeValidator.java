package eu.virtualparadox.managedpostgres.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Validates local PostgreSQL runtime directory structure.
 */
public final class RuntimeValidator {

    private RuntimeValidator() {
    }

    /**
     * Requires a runtime directory containing the PostgreSQL control and server binaries.
     *
     * @param runtimeDirectory candidate runtime directory
     * @return normalized runtime directory
     */
    public static Path requireUsableRuntimeDirectory(final Path runtimeDirectory) {
        final Path normalizedRuntimeDirectory = Objects.requireNonNull(runtimeDirectory, "runtimeDirectory")
                .toAbsolutePath()
                .normalize();
        requireDirectory(normalizedRuntimeDirectory, "runtimeDirectory");
        requireRegularFile(normalizedRuntimeDirectory.resolve("bin").resolve("pg_ctl"), "pg_ctl");
        requireRegularFile(normalizedRuntimeDirectory.resolve("bin").resolve("psql"), "psql");
        requireRegularFile(normalizedRuntimeDirectory.resolve("bin").resolve("postgres"), "postgres");

        return normalizedRuntimeDirectory;
    }

    private static void requireDirectory(final Path path, final String label) {
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(label + " must be an existing directory: " + path);
        }
    }

    private static void requireRegularFile(final Path path, final String binaryName) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("runtime directory requires bin/" + binaryName + ": " + path);
        }
    }
}
