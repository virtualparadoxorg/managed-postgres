package eu.virtualparadox.managedpostgres.observe;

import java.util.Objects;

/**
 * Immutable PostgreSQL server log line delivered to a {@link PostgresLogListener}.
 *
 * @param level severity level of the line
 * @param source origin of the line
 * @param message log line text
 */
public record PostgresLogLine(PostgresLogLevel level, PostgresLogSource source, String message) {

    /**
     * Creates an immutable PostgreSQL log line.
     *
     * @param level severity level of the line
     * @param source origin of the line
     * @param message log line text
     */
    public PostgresLogLine {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(message, "message");
    }
}
