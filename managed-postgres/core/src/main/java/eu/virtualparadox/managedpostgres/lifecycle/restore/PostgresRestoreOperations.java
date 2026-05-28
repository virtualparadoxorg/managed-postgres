package eu.virtualparadox.managedpostgres.lifecycle.restore;

import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.util.List;
import java.util.Map;

/**
 * Coordinates postgres restore operations behavior for managed PostgreSQL internals.
 */
public final class PostgresRestoreOperations {

    private PostgresRestoreOperations() {
    }

    /**
     * Returns the unsupported result.
     *
     * @return unsupported result
     */
    public static PostgresRestoreOperation unsupported() {
        return (backup, options) -> {
            throw new PostgresRestoreException(
                    "PostgreSQL logical restore execution is not configured",
                    new DiagnosticReport(List.of(new DiagnosticSection(
                            "postgres-restore",
                            Map.of("reason", "restore execution is not configured")))));
        };
    }
}
