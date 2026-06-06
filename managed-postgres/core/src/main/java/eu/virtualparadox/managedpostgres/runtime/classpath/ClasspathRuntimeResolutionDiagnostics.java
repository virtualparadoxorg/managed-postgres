package eu.virtualparadox.managedpostgres.runtime.classpath;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates diagnostics for classpath runtime resolution failures.
 */
public final class ClasspathRuntimeResolutionDiagnostics {

    private ClasspathRuntimeResolutionDiagnostics() {}

    /**
     * Creates a classpath runtime resolution failure.
     *
     * @param message failure message
     * @param runtimeSource runtime source
     * @return managed PostgreSQL exception
     */
    public static ManagedPostgresException failure(final String message, final RuntimeSource runtimeSource) {
        return new ManagedPostgresException(message, diagnostic(runtimeSource));
    }

    /**
     * Creates a classpath runtime resolution failure.
     *
     * @param message failure message
     * @param runtimeSource runtime source
     * @param cause failure cause
     * @return managed PostgreSQL exception
     */
    public static ManagedPostgresException failure(
            final String message, final RuntimeSource runtimeSource, final Throwable cause) {
        return new ManagedPostgresException(message, cause, diagnostic(runtimeSource));
    }

    private static DiagnosticReport diagnostic(final RuntimeSource runtimeSource) {
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("runtimeSource", runtimeSource.kind());

        return new DiagnosticReport(List.of(new DiagnosticSection("runtime-resolution", values)));
    }
}
