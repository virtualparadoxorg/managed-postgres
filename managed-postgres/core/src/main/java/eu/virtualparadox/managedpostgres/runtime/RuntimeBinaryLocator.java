package eu.virtualparadox.managedpostgres.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves PostgreSQL runtime binaries from a managed runtime directory.
 */
public final class RuntimeBinaryLocator {

    private RuntimeBinaryLocator() {
    }

    /**
     * Resolves a required PostgreSQL binary from the runtime directory.
     *
     * @param runtimeDirectory PostgreSQL runtime directory
     * @param binaryName logical binary name without extension
     * @return path to the runtime binary
     */
    public static Path requireBinary(final Path runtimeDirectory, final String binaryName) {
        final Path resolved = resolveBinary(runtimeDirectory, binaryName);
        if (!Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException(
                    "runtime directory requires bin/" + binaryName + " or bin/" + binaryName + ".exe: " + resolved);
        }

        return resolved;
    }

    /**
     * Resolves the most suitable runtime binary path, preferring the plain name over the Windows extension.
     *
     * @param runtimeDirectory PostgreSQL runtime directory
     * @param binaryName logical binary name without extension
     * @return preferred runtime binary path
     */
    public static Path resolveBinary(final Path runtimeDirectory, final String binaryName) {
        final Path binDirectory = Objects.requireNonNull(runtimeDirectory, "runtimeDirectory")
                .resolve("bin");
        final String checkedBinaryName = Objects.requireNonNull(binaryName, "binaryName");
        final Path plainExecutable = binDirectory.resolve(checkedBinaryName);
        final Path windowsExecutable = binDirectory.resolve(checkedBinaryName + ".exe");
        Path executable = plainExecutable;
        if (!Files.isRegularFile(plainExecutable) && Files.isRegularFile(windowsExecutable)) {
            executable = windowsExecutable;
        }

        return executable;
    }
}
