package eu.virtualparadox.managedpostgres.lifecycle.handle;

import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperationProvider;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreOperationProvider;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreOperations;

/**
 * Operation providers attached to a running PostgreSQL handle.
 *
 * @param backup logical backup operation provider
 * @param restore logical restore operation provider
 */
public record PostgresHandleOperationProviders(
        PostgresBackupOperationProvider backup,
        PostgresRestoreOperationProvider restore) {

    /**
     * Defines the value value.
     */
    public PostgresHandleOperationProviders {
        Objects.requireNonNull(backup, "backup");
        Objects.requireNonNull(restore, "restore");
    }

    /**
     * Returns the unsupported restore result.
     *
     * @param backup backup value
     * @return unsupported restore result
     */
    public static PostgresHandleOperationProviders unsupportedRestore(final PostgresBackupOperationProvider backup) {
        return new PostgresHandleOperationProviders(
                backup,
                context -> PostgresRestoreOperations.unsupported());
    }
}
