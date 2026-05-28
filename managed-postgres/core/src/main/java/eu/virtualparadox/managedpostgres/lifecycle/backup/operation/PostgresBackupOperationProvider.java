package eu.virtualparadox.managedpostgres.lifecycle.backup.operation;

/**
 * Creates logical backup operations for running PostgreSQL handles.
 */
@FunctionalInterface
public interface PostgresBackupOperationProvider {

    /**
     * Creates a backup operation for the supplied running PostgreSQL context.
     *
     * @param context running PostgreSQL context
     * @return backup operation
     */
    public PostgresBackupOperation create(PostgresBackupOperationContext context);
}
