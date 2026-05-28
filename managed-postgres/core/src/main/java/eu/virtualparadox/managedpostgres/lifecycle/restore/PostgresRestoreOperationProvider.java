package eu.virtualparadox.managedpostgres.lifecycle.restore;

import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperationContext;

/**
 * Creates logical restore operations for a running PostgreSQL instance.
 */
@FunctionalInterface
public interface PostgresRestoreOperationProvider {

    /**
     * Returns the create result.
     *
     * @param context context value
     * @return create result
     */
    public PostgresRestoreOperation create(PostgresBackupOperationContext context);
}
