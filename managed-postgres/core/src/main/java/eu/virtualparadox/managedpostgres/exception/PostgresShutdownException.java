package eu.virtualparadox.managedpostgres.exception;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;

/**
 * Raised when managed PostgreSQL cannot be stopped cleanly.
 */
public final class PostgresShutdownException extends ManagedPostgresException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a PostgreSQL shutdown exception with diagnostics.
     *
     * @param message exception message
     * @param diagnosticReport diagnostic report
     */
    public PostgresShutdownException(final String message, final DiagnosticReport diagnosticReport) {
        super(message, diagnosticReport);
    }

    /**
     * Creates a PostgreSQL shutdown exception with diagnostics and a cause.
     *
     * @param message exception message
     * @param cause exception cause
     * @param diagnosticReport diagnostic report
     */
    public PostgresShutdownException(
            final String message, final Throwable cause, final DiagnosticReport diagnosticReport) {
        super(message, cause, diagnosticReport);
    }
}
