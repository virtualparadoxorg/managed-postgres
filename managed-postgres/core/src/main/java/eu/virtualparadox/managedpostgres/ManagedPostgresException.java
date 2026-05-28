package eu.virtualparadox.managedpostgres;

import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import java.util.Objects;

/**
 * Domain exception raised by managed PostgreSQL lifecycle operations.
 */
public class ManagedPostgresException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Diagnostic report captured when the lifecycle failure was raised.
     */
    private final DiagnosticReport diagnosticReport;

    /**
     * Creates a managed PostgreSQL exception with diagnostics.
     *
     * @param message exception message
     * @param diagnosticReport diagnostic report
     */
    public ManagedPostgresException(final String message, final DiagnosticReport diagnosticReport) {
        super(message);
        this.diagnosticReport = Objects.requireNonNull(diagnosticReport, "diagnosticReport");
    }

    /**
     * Creates a managed PostgreSQL exception with diagnostics and a cause.
     *
     * @param message exception message
     * @param cause exception cause
     * @param diagnosticReport diagnostic report
     */
    public ManagedPostgresException(
            final String message,
            final Throwable cause,
            final DiagnosticReport diagnosticReport) {
        super(message, cause);
        this.diagnosticReport = Objects.requireNonNull(diagnosticReport, "diagnosticReport");
    }

    /**
     * Returns the diagnostic report captured with this exception.
     *
     * @return diagnostic report
     */
    public DiagnosticReport diagnosticReport() {
        return diagnosticReport;
    }
}
