package eu.virtualparadox.managedpostgres.dsl;

/**
 * Fluent section for PostgreSQL server tuning.
 *
 * <p>Entered with {@link ManagedPostgresBuilder#serverConfiguration()}. It extends the builder, so
 * settings chain directly and any builder method continues configuration fluently up to {@code build()}.
 */
public interface ConfigurationSection extends ManagedPostgresBuilder {

    /**
     * Sets {@code max_connections}.
     *
     * @param value maximum concurrent connections
     * @return the server configuration section
     */
    ConfigurationSection maxConnections(int value);

    /**
     * Sets {@code shared_buffers}.
     *
     * @param value shared buffers size (e.g. {@code 192MB})
     * @return the server configuration section
     */
    ConfigurationSection sharedBuffers(String value);

    /**
     * Sets {@code temp_buffers}.
     *
     * @param value temp buffers size (e.g. {@code 16MB})
     * @return the server configuration section
     */
    ConfigurationSection tempBuffers(String value);

    /**
     * Sets {@code statement_timeout} in seconds.
     *
     * @param value statement timeout in seconds
     * @return the server configuration section
     */
    ConfigurationSection statementTimeoutSeconds(int value);
}
