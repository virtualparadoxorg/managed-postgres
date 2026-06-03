package eu.virtualparadox.managedpostgres.lifecycle.restore;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Coordinates postgres restore failures behavior for managed PostgreSQL internals.
 */
public final class PostgresRestoreFailures {

    private PostgresRestoreFailures() {}

    /**
     * Returns the not running result.
     *
     * @param status status value
     * @return not running result
     */
    public static PostgresRestoreException notRunning(final PostgresStatus status) {
        final PostgresStatus checkedStatus = Objects.requireNonNull(status, "status");

        return new PostgresRestoreException(
                "Cannot restore PostgreSQL backup because the handle is not running",
                diagnostic(Map.of("status", checkedStatus.name())));
    }

    /**
     * Returns the drop current database required result.
     *
     * @return drop current database required result
     */
    public static PostgresRestoreException dropCurrentDatabaseRequired() {
        return new PostgresRestoreException(
                "Cannot restore PostgreSQL backup because dropCurrentDatabase is false",
                diagnostic(Map.of("dropCurrentDatabase", "false")));
    }

    private static DiagnosticReport diagnostic(final Map<String, String> values) {
        return new DiagnosticReport(List.of(new DiagnosticSection("postgres-restore", values)));
    }
}
