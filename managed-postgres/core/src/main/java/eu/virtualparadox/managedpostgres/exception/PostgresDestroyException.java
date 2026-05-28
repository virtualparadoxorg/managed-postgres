package eu.virtualparadox.managedpostgres.exception;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;

/**
 * Managed PostgreSQL explicit destroy failure.
 */
public final class PostgresDestroyException extends ManagedPostgresException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a destroy exception with diagnostics.
     *
     * @param message failure message
     * @param diagnosticReport structured diagnostics
     */
    public PostgresDestroyException(final String message, final DiagnosticReport diagnosticReport) {
        super(message, diagnosticReport);
    }

    /**
     * Creates a destroy exception with diagnostics and a cause.
     *
     * @param message failure message
     * @param cause failure cause
     * @param diagnosticReport structured diagnostics
     */
    public PostgresDestroyException(
            final String message,
            final Throwable cause,
            final DiagnosticReport diagnosticReport) {
        super(message, cause, diagnosticReport);
    }
}
