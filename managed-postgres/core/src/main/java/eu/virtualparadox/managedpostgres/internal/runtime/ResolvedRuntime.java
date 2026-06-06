package eu.virtualparadox.managedpostgres.internal.runtime;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable runtime resolution result for internal lifecycle telemetry.
 *
 * @param runtimeDirectory resolved PostgreSQL runtime directory
 * @param installDuration time spent publishing a new runtime into the cache, or zero when no install occurred
 */
public record ResolvedRuntime(Path runtimeDirectory, Duration installDuration) {

    /**
     * Creates an immutable resolved runtime result.
     *
     * @param runtimeDirectory resolved PostgreSQL runtime directory
     * @param installDuration time spent publishing a new runtime into the cache, or zero when no install occurred
     */
    public ResolvedRuntime {
        final Duration checkedInstallDuration = Objects.requireNonNull(installDuration, "installDuration");
        final Path checkedRuntimeDirectory = Objects.requireNonNull(runtimeDirectory, "runtimeDirectory");
        if (checkedInstallDuration.isNegative()) {
            throw new IllegalArgumentException("installDuration must not be negative");
        }
        runtimeDirectory = checkedRuntimeDirectory;
        installDuration = checkedInstallDuration;
    }
}
