package eu.virtualparadox.managedpostgres;

import java.nio.file.Path;

/**
 * Handle for a running PostgreSQL instance.
 */
public interface RunningPostgres extends AutoCloseable {

    /**
     * Returns connection details for the running instance.
     *
     * @return connection details
     */
    public PostgresConnectionInfo connectionInfo();

    /**
     * Returns the current lifecycle status.
     *
     * @return current lifecycle status
     */
    public PostgresStatus status();

    /**
     * Creates a logical backup at the supplied target path.
     *
     * @param target backup target file
     */
    public void backupTo(Path target);

    /**
     * Restores a logical backup with explicit restore options.
     *
     * @param backup backup file to restore
     * @param options restore options
     */
    public void restoreFrom(Path backup, RestoreOptions options);

    /**
     * Stops the running PostgreSQL instance.
     */
    public void stop();

    /**
     * Closes this running PostgreSQL handle.
     */
    @Override
    public void close();
}
