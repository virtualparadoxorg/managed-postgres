package eu.virtualparadox.managedpostgres.lifecycle.layout;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds PostgreSQL lifecycle lock failures with diagnostics.
 */
public final class PostgresLockFailure {

    private PostgresLockFailure() {}

    /**
     * Returns the create result.
     *
     * @param message message value
     * @param path path value
     * @return create result
     */
    public static ManagedPostgresException create(final String message, final Path path) {
        return new ManagedPostgresException(message, diagnostic(path));
    }

    /**
     * Returns the create result.
     *
     * @param message message value
     * @param path path value
     * @param cause cause value
     * @return create result
     */
    public static ManagedPostgresException create(final String message, final Path path, final Throwable cause) {
        return new ManagedPostgresException(message, cause, diagnostic(path));
    }

    private static DiagnosticReport diagnostic(final Path path) {
        return new DiagnosticReport(List.of(new DiagnosticSection(
                "postgres-lock",
                Map.of("path", Objects.requireNonNull(path, "path").toString()))));
    }
}
