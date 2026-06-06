package eu.virtualparadox.managedpostgres.observe;

/**
 * Progress listener that discards every event.
 */
final class NoOpManagedPostgresProgressListener implements ManagedPostgresProgressListener {

    /** Shared singleton instance. */
    static final NoOpManagedPostgresProgressListener INSTANCE = new NoOpManagedPostgresProgressListener();

    private NoOpManagedPostgresProgressListener() {}

    @Override
    public void onProgress(final StartupProgress progress) {
        // Intentionally ignores all progress.
    }
}
