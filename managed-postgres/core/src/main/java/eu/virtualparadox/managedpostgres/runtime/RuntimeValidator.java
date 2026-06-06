package eu.virtualparadox.managedpostgres.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Validates local PostgreSQL runtime directory structure.
 */
public final class RuntimeValidator {

    private RuntimeValidator() {}

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
        RuntimeBinaryLocator.requireBinary(normalizedRuntimeDirectory, "pg_ctl");
        RuntimeBinaryLocator.requireBinary(normalizedRuntimeDirectory, "psql");
        RuntimeBinaryLocator.requireBinary(normalizedRuntimeDirectory, "postgres");

        return normalizedRuntimeDirectory;
    }

    private static void requireDirectory(final Path path, final String label) {
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(label + " must be an existing directory: " + path);
        }
    }
}
