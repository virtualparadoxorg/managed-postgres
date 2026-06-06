package eu.virtualparadox.managedpostgres.config.model;

/**
 * Policy for runtime or data upgrades.
 */
public enum UpgradePolicy {
    /**
     * Reject any PostgreSQL version change.
     */
    DISABLED,

    /**
     * Allow compatible PostgreSQL minor version changes.
     */
    MINOR_ONLY
}
