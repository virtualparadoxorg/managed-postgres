package eu.virtualparadox.managedpostgres.lifecycle.status;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable status probe snapshot.
 *
 * @param status PostgreSQL status
 * @param diagnosticReport diagnostic report, when status resolution produced one
 */
public record PostgresStatusSnapshot(PostgresStatus status, Optional<DiagnosticReport> diagnosticReport) {

    /**
     * Creates a status probe snapshot.
     *
     * @param status PostgreSQL status
     * @param diagnosticReport diagnostic report, when status resolution produced one
     */
    public PostgresStatusSnapshot {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(diagnosticReport, "diagnosticReport");
    }

    /**
     * Creates a running status snapshot.
     *
     * @return running status snapshot
     */
    public static PostgresStatusSnapshot running() {
        return new PostgresStatusSnapshot(PostgresStatus.RUNNING, Optional.empty());
    }

    /**
     * Creates a failed status snapshot with diagnostics.
     *
     * @param diagnosticReport diagnostic report
     * @return failed status snapshot
     */
    public static PostgresStatusSnapshot failed(final DiagnosticReport diagnosticReport) {
        return new PostgresStatusSnapshot(
                PostgresStatus.FAILED, Optional.of(Objects.requireNonNull(diagnosticReport, "diagnosticReport")));
    }
}
