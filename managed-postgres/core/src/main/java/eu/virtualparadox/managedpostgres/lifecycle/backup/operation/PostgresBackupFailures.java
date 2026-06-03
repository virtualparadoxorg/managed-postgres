package eu.virtualparadox.managedpostgres.lifecycle.backup.operation;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.exception.PostgresBackupException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Coordinates postgres backup failures behavior for managed PostgreSQL internals.
 */
public final class PostgresBackupFailures {

    private PostgresBackupFailures() {}

    /**
     * Returns the not running result.
     *
     * @param status status value
     * @return not running result
     */
    public static PostgresBackupException notRunning(final PostgresStatus status) {
        final PostgresStatus checkedStatus = Objects.requireNonNull(status, "status");

        return new PostgresBackupException(
                "Cannot create PostgreSQL backup because the handle is not running",
                new DiagnosticReport(
                        List.of(new DiagnosticSection("postgres-backup", Map.of("status", checkedStatus.name())))));
    }
}
