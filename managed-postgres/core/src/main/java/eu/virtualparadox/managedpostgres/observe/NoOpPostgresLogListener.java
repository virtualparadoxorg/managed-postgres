package eu.virtualparadox.managedpostgres.observe;

/**
 * Log listener that discards every line.
 */
final class NoOpPostgresLogListener implements PostgresLogListener {

    /** Shared singleton instance. */
    static final NoOpPostgresLogListener INSTANCE = new NoOpPostgresLogListener();

    private NoOpPostgresLogListener() {}

    @Override
    public void onLogLine(final PostgresLogLine line) {
        // Intentionally ignores all log lines.
    }

    @Override
    public boolean isActive() {
        return false;
    }
}
