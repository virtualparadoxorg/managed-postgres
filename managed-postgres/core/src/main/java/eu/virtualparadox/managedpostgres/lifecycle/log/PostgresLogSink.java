package eu.virtualparadox.managedpostgres.lifecycle.log;

/**
 * Internal sink for forwarding PostgreSQL process log lines.
 */
@FunctionalInterface
public interface PostgresLogSink {

    /**
     * Forwards one PostgreSQL process log line.
     *
     * @param loggerName SLF4J logger name
     * @param line redacted log line
     */
    void log(String loggerName, String line);
}
