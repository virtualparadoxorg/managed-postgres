package eu.virtualparadox.managedpostgres;

/**
 * Managed PostgreSQL lifecycle status.
 */
public enum PostgresStatus {
    /**
     * PostgreSQL is not running.
     */
    STOPPED,

    /**
     * PostgreSQL is starting.
     */
    STARTING,

    /**
     * PostgreSQL is running.
     */
    RUNNING,

    /**
     * PostgreSQL is stopping.
     */
    STOPPING,

    /**
     * PostgreSQL failed.
     */
    FAILED
}
