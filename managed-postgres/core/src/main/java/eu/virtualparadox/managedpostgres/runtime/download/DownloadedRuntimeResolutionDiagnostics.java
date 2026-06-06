package eu.virtualparadox.managedpostgres.runtime.download;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.util.List;
import java.util.Map;

/**
 * Coordinates downloaded runtime resolution diagnostics behavior for managed PostgreSQL internals.
 */
public final class DownloadedRuntimeResolutionDiagnostics {

    private DownloadedRuntimeResolutionDiagnostics() {}

    /**
     * Returns the failure result.
     *
     * @param message message value
     * @param runtimeSource runtime source value
     * @return failure result
     */
    public static ManagedPostgresException failure(final String message, final RuntimeSource runtimeSource) {
        return new ManagedPostgresException(message, diagnostic(runtimeSource, message));
    }

    /**
     * Returns the failure result.
     *
     * @param message message value
     * @param runtimeSource runtime source value
     * @param cause cause value
     * @return failure result
     */
    public static ManagedPostgresException failure(
            final String message, final RuntimeSource runtimeSource, final Throwable cause) {
        return new ManagedPostgresException(message, cause, diagnostic(runtimeSource, message));
    }

    private static DiagnosticReport diagnostic(final RuntimeSource runtimeSource, final String message) {
        return new DiagnosticReport(List.of(new DiagnosticSection(
                "runtime-resolution", Map.of("runtimeSource", runtimeSource.kind(), "message", message))));
    }
}
