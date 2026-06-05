package eu.virtualparadox.managedpostgres.observe;

/**
 * Receives managed PostgreSQL startup progress events.
 *
 * <p>Listeners are registered as objects via
 * {@code ManagedPostgresBuilder.onProgress(ManagedPostgresProgressListener)}; the public DSL stays
 * lambda-free in shape, so prefer the provided named implementations or a dedicated class.
 */
public interface ManagedPostgresProgressListener {

    /**
     * Called for each startup progress event.
     *
     * @param progress progress event
     */
    void onProgress(StartupProgress progress);

    /**
     * Returns a listener that logs progress via SLF4J.
     *
     * @return SLF4J-backed progress listener
     */
    static ManagedPostgresProgressListener slf4j() {
        return Slf4jManagedPostgresProgressListener.INSTANCE;
    }

    /**
     * Returns a listener that ignores all progress.
     *
     * @return no-op progress listener
     */
    static ManagedPostgresProgressListener none() {
        return NoOpManagedPostgresProgressListener.INSTANCE;
    }
}
