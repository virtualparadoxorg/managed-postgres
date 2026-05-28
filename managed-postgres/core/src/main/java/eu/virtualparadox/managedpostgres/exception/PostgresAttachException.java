package eu.virtualparadox.managedpostgres.exception;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;

/**
 * Exception thrown when attaching to an existing PostgreSQL instance fails.
 */
public final class PostgresAttachException extends ManagedPostgresException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an attach exception.
     *
     * @param message exception message
     * @param diagnosticReport structured diagnostic report
     */
    public PostgresAttachException(final String message, final DiagnosticReport diagnosticReport) {
        super(message, diagnosticReport);
    }

    /**
     * Creates an attach exception with a cause.
     *
     * @param message exception message
     * @param cause exception cause
     * @param diagnosticReport structured diagnostic report
     */
    public PostgresAttachException(
            final String message,
            final Throwable cause,
            final DiagnosticReport diagnosticReport) {
        super(message, cause, diagnosticReport);
    }
}
