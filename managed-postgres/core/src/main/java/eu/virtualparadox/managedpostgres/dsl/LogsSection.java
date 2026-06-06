package eu.virtualparadox.managedpostgres.dsl;

import eu.virtualparadox.managedpostgres.observe.PostgresLogListener;

/**
 * Fluent section for PostgreSQL process log handling.
 *
 * <p>Entered with {@link ManagedPostgresBuilder#logs()}. It extends the builder, so settings chain
 * directly and any builder method continues configuration fluently up to {@code build()}.
 */
public interface LogsSection extends ManagedPostgresBuilder {

    /**
     * Writes PostgreSQL logs to files only (no SLF4J bridging). Clears any previously set SLF4J bridge.
     *
     * @return the logs section
     */
    LogsSection toFiles();

    /**
     * Bridges new PostgreSQL log lines to SLF4J in addition to files.
     *
     * @return the logs section
     */
    LogsSection toSlf4j();

    /**
     * Sets the SLF4J logger name used when bridging is enabled.
     *
     * @param loggerName SLF4J logger name
     * @return the logs section
     */
    LogsSection loggerName(String loggerName);

    /**
     * Sends PostgreSQL server log lines to the given listener (and turns the SLF4J bridge off).
     *
     * @param listener log listener
     * @return the logs section, so configuration can continue
     */
    public LogsSection toListener(PostgresLogListener listener);
}
