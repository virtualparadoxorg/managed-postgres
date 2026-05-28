package eu.virtualparadox.managedpostgres.config.postgresql;

/**
 * Safe PostgreSQL resource presets for local development, tests, and CI.
 */
public final class Resources {

    private Resources() {
    }

    /**
     * Returns a tiny preset intended for short-lived temporary test clusters.
     *
     * @return tiny PostgreSQL resource preset
     */
    public static PostgresConfiguration tiny() {
        return PostgresConfiguration.defaults()
                .maxConnections(16)
                .sharedBuffers("64MB")
                .tempBuffers("8MB")
                .statementTimeoutSeconds(15);
    }

    /**
     * Returns a small preset intended for persistent local development.
     *
     * @return small PostgreSQL resource preset
     */
    public static PostgresConfiguration small() {
        return PostgresConfiguration.defaults()
                .maxConnections(32)
                .sharedBuffers("128MB")
                .tempBuffers("16MB")
                .statementTimeoutSeconds(30);
    }

    /**
     * Returns a CI preset intended for deterministic automated builds.
     *
     * @return CI PostgreSQL resource preset
     */
    public static PostgresConfiguration ci() {
        return PostgresConfiguration.defaults()
                .maxConnections(24)
                .sharedBuffers("96MB")
                .tempBuffers("8MB")
                .statementTimeoutSeconds(30);
    }
}
