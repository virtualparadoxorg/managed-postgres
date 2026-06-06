package eu.virtualparadox.managedpostgres.config.model;

/**
 * Policy for handling drift between stored and requested configuration.
 */
public enum ConfigDriftPolicy {
    /**
     * Fail when stored configuration differs from requested configuration.
     */
    FAIL,

    /**
     * Ignore stored configuration drift.
     */
    IGNORE
}
