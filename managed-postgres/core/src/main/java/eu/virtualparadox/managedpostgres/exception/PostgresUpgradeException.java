package eu.virtualparadox.managedpostgres.exception;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;

/**
 * Exception thrown when PostgreSQL version or upgrade preflight fails.
 */
public final class PostgresUpgradeException extends ManagedPostgresException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an upgrade exception.
     *
     * @param message exception message
     * @param diagnosticReport structured diagnostic report
     */
    public PostgresUpgradeException(final String message, final DiagnosticReport diagnosticReport) {
        super(message, diagnosticReport);
    }

    /**
     * Creates an upgrade exception with a cause.
     *
     * @param message exception message
     * @param cause exception cause
     * @param diagnosticReport structured diagnostic report
     */
    public PostgresUpgradeException(
            final String message,
            final Throwable cause,
            final DiagnosticReport diagnosticReport) {
        super(message, cause, diagnosticReport);
    }
}
