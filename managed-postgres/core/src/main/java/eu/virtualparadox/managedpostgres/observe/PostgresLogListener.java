package eu.virtualparadox.managedpostgres.observe;

/**
 * Receives PostgreSQL server log lines.
 *
 * <p>Listeners are registered as objects via {@code ManagedPostgresBuilder.logs().toListener(...)};
 * the public DSL stays lambda-free in shape, so prefer a dedicated class over an inline lambda.
 */
public interface PostgresLogListener {

    /**
     * Called for each captured PostgreSQL server log line.
     *
     * @param line log line
     */
    void onLogLine(PostgresLogLine line);

    /**
     * Returns a listener that ignores all log lines.
     *
     * @return no-op log listener
     */
    static PostgresLogListener none() {
        return NoOpPostgresLogListener.INSTANCE;
    }
}
