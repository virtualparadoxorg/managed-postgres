package eu.virtualparadox.managedpostgres.config;

/**
 * Policy for stopping PostgreSQL when a handle is closed.
 */
public enum StopPolicy {
    /**
     * Stop PostgreSQL when the handle is closed.
     */
    STOP_ON_CLOSE,

    /**
     * Leave PostgreSQL running when the handle is closed.
     */
    KEEP_RUNNING
}
