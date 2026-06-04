package eu.virtualparadox.managedpostgres;

/**
 * Fluent section for loopback-only PostgreSQL network configuration.
 *
 * <p>Entered with {@link ManagedPostgresBuilder#network()}. It extends the builder, so settings chain
 * directly and any builder method continues configuration fluently up to {@code build()}.
 */
public interface NetworkSection extends ManagedPostgresBuilder {

    /**
     * Sets an explicit loopback listen host.
     *
     * @param host PostgreSQL listen host
     * @return the network section
     */
    NetworkSection host(String host);

    /**
     * Uses a fixed port that must be available.
     *
     * @param port PostgreSQL port
     * @return the network section
     */
    NetworkSection port(int port);

    /**
     * Uses any currently available loopback port.
     *
     * @return the network section
     */
    NetworkSection randomPort();

    /**
     * Uses a metadata-backed stable random port.
     *
     * @return the network section
     */
    NetworkSection stableRandomPort();

    /**
     * Prefers a port, failing when occupied unless {@link #fallbackToRandom()} is applied.
     *
     * @param port preferred PostgreSQL port
     * @return the network section
     */
    NetworkSection preferredPort(int port);

    /**
     * Allows preferred-port selection to fall back to a random available port.
     *
     * @return the network section
     */
    NetworkSection fallbackToRandom();
}
