package eu.virtualparadox.managedpostgres.exception;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;

/**
 * Managed PostgreSQL explicit cleanup failure.
 */
public final class PostgresCleanupException extends ManagedPostgresException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a cleanup exception with diagnostics.
     *
     * @param message failure message
     * @param diagnosticReport structured diagnostics
     */
    public PostgresCleanupException(final String message, final DiagnosticReport diagnosticReport) {
        super(message, diagnosticReport);
    }

    /**
     * Creates a cleanup exception with diagnostics and a cause.
     *
     * @param message failure message
     * @param cause failure cause
     * @param diagnosticReport structured diagnostics
     */
    public PostgresCleanupException(
            final String message, final Throwable cause, final DiagnosticReport diagnosticReport) {
        super(message, cause, diagnosticReport);
    }
}
