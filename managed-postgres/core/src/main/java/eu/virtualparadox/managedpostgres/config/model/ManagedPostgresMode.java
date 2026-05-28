package eu.virtualparadox.managedpostgres.config.model;

/**
 * Managed PostgreSQL operating mode.
 */
public enum ManagedPostgresMode {
    /**
     * Persistent project-local PostgreSQL mode.
     */
    PERSISTENT_LOCAL,

    /**
     * Temporary PostgreSQL mode.
     */
    TEMPORARY
}
