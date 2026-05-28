package eu.virtualparadox.managedpostgres.lifecycle.restore;

import eu.virtualparadox.managedpostgres.RestoreOptions;
import java.nio.file.Path;

/**
 * Internal logical restore operation used by PostgreSQL handles.
 */
@FunctionalInterface
public interface PostgresRestoreOperation {

    /**
     * Restores a logical backup.
     *
     * @param backup backup file to restore
     * @param options restore options
     */
    public void restoreFrom(Path backup, RestoreOptions options);
}
