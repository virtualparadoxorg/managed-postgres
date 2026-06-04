package eu.virtualparadox.managedpostgres;

/**
 * Fluent section for the primary application database bootstrap.
 *
 * <p>Entered with {@link ManagedPostgresBuilder#cluster()}. It extends the builder, so settings chain
 * directly and any builder method continues configuration fluently up to {@code build()}.
 */
public interface ClusterSection extends ManagedPostgresBuilder {

    /**
     * Sets the primary application database name.
     *
     * @param database application database name
     * @return the cluster section
     */
    ClusterSection database(String database);

    /**
     * Overrides the application owner role.
     *
     * @param owner application owner role
     * @return the cluster section
     */
    ClusterSection owner(String owner);

    /**
     * Overrides the application owner password.
     *
     * @param password application owner password
     * @return the cluster section
     */
    ClusterSection password(String password);

    /**
     * Requests a required PostgreSQL extension (startup fails if it is unavailable). Repeatable.
     *
     * @param extensionName PostgreSQL extension name
     * @return the cluster section
     */
    ClusterSection extension(String extensionName);

    /**
     * Requests an optional PostgreSQL extension (skipped if unavailable). Repeatable.
     *
     * @param extensionName PostgreSQL extension name
     * @return the cluster section
     */
    ClusterSection optionalExtension(String extensionName);
}
