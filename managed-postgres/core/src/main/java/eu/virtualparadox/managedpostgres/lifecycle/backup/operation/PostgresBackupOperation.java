package eu.virtualparadox.managedpostgres.lifecycle.backup.operation;

import java.nio.file.Path;

/**
 * Defines the postgres backup operation contract for managed PostgreSQL internals.
 */
@FunctionalInterface
public interface PostgresBackupOperation {

    /**
     * Performs the backup to operation.
     *
     * @param target target value
     */
    public void backupTo(Path target);
}
