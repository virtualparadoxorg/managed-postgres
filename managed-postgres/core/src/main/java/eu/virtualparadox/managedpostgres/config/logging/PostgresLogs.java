package eu.virtualparadox.managedpostgres.config.logging;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Immutable PostgreSQL process log forwarding configuration.
 *
 * @param bridgeToSlf4j whether new PostgreSQL log lines should also be bridged to SLF4J
 * @param loggerName SLF4J logger name to use when bridging is enabled
 */
public record PostgresLogs(boolean bridgeToSlf4j, String loggerName) {

    private static final String DEFAULT_LOGGER_NAME = ManagedPostgres.class.getPackageName() + ".postgresql";

    /**
     * Creates immutable PostgreSQL log forwarding configuration.
     *
     * @param bridgeToSlf4j whether new PostgreSQL log lines should also be bridged to SLF4J
     * @param loggerName SLF4J logger name to use when bridging is enabled
     */
    public PostgresLogs {
        if (StringUtils.isBlank(loggerName)) {
            throw new IllegalArgumentException("loggerName must not be blank");
        }
    }

    /**
     * Creates default file-only PostgreSQL log handling.
     *
     * @return default PostgreSQL log handling
     */
    public static PostgresLogs defaults() {
        return new PostgresLogs(false, DEFAULT_LOGGER_NAME);
    }

    /**
     * Returns file-only log handling.
     *
     * @return updated log handling
     */
    public PostgresLogs toFiles() {
        return new PostgresLogs(false, loggerName);
    }

    /**
     * Returns file handling plus SLF4J log bridging.
     *
     * @return updated log handling
     */
    public PostgresLogs toSlf4j() {
        return new PostgresLogs(true, loggerName);
    }

    /**
     * Returns log handling with another SLF4J logger name.
     *
     * @param newLoggerName SLF4J logger name
     * @return updated log handling
     */
    public PostgresLogs loggerName(final String newLoggerName) {
        return new PostgresLogs(bridgeToSlf4j, Objects.requireNonNull(newLoggerName, "newLoggerName"));
    }
}
