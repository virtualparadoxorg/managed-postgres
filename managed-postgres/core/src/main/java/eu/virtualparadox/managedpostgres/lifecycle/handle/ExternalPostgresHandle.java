package eu.virtualparadox.managedpostgres.lifecycle.handle;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.exception.PostgresBackupException;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperation;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreOperation;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreOperations;
import java.util.List;
import java.util.Map;

/**
 * Handle for a validated externally managed PostgreSQL connection.
 */
public final class ExternalPostgresHandle extends AbstractPostgresHandle {

    /**
     * Creates an external PostgreSQL handle.
     *
     * @param connectionInfo validated connection details
     */
    public ExternalPostgresHandle(final PostgresConnectionInfo connectionInfo) {
        super(connectionInfo, unsupportedBackup(), unsupportedRestore());
    }

    /**
     * Detaches this external PostgreSQL handle without stopping the database.
     */
    @Override
    public void stop() {
        setStatus(PostgresStatus.STOPPED);
    }

    /**
     * Closes this external PostgreSQL handle without stopping the database.
     */
    @Override
    public void close() {
        stop();
    }

    private static PostgresBackupOperation unsupportedBackup() {
        return target -> {
            throw new PostgresBackupException(
                    "PostgreSQL logical backup execution is not configured for external mode",
                    new DiagnosticReport(List.of(new DiagnosticSection(
                            "postgres-backup",
                            Map.of("reason", "external mode has no managed runtime commands")))));
        };
    }

    private static PostgresRestoreOperation unsupportedRestore() {
        return PostgresRestoreOperations.unsupported();
    }
}
