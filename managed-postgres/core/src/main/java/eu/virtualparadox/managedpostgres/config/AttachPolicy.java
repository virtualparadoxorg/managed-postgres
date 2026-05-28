package eu.virtualparadox.managedpostgres.config;

/**
 * Policy for attaching to existing managed PostgreSQL data.
 */
public enum AttachPolicy {
    /**
     * Always create a new managed PostgreSQL instance.
     */
    CREATE_NEW,

    /**
     * Attach only when existing data is compatible.
     */
    ATTACH_IF_COMPATIBLE
}
