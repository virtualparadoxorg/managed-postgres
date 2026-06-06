package eu.virtualparadox.managedpostgres.lifecycle.log;

import eu.virtualparadox.managedpostgres.observe.PostgresLogLevel;
import eu.virtualparadox.managedpostgres.observe.PostgresLogLine;
import eu.virtualparadox.managedpostgres.observe.PostgresLogListener;
import eu.virtualparadox.managedpostgres.observe.PostgresLogSource;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sink that wraps each PostgreSQL log line as a structured {@link PostgresLogLine} and delivers it to a
 * {@link PostgresLogListener}.
 *
 * <p>The incoming {@code line} is already redacted by {@link TailingPostgresLogBridge}.
 */
public final class ListenerPostgresLogSink implements PostgresLogSink {

    /**
     * Matches the first uppercase severity keyword followed by a colon, anchored on a non-letter boundary so that
     * substrings of larger words are not mistaken for a severity token.
     */
    private static final Pattern SEVERITY_PATTERN =
            Pattern.compile("(?<![A-Za-z])(DEBUG|INFO|NOTICE|WARNING|LOG|ERROR|FATAL|PANIC):");

    private final PostgresLogListener listener;

    /**
     * Creates a structured listener sink.
     *
     * @param listener listener receiving the structured log lines
     */
    public ListenerPostgresLogSink(final PostgresLogListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void log(final String loggerName, final String line) {
        Objects.requireNonNull(loggerName, "loggerName");
        final String checkedLine = Objects.requireNonNull(line, "line");
        listener.onLogLine(new PostgresLogLine(parseLevel(checkedLine), PostgresLogSource.SERVER, checkedLine));
    }

    /**
     * Best-effort severity detection: maps the first uppercase {@code LEVEL:} keyword found in the line to a
     * {@link PostgresLogLevel}; returns {@link PostgresLogLevel#UNKNOWN} when none is found.
     *
     * @param line redacted PostgreSQL log line
     * @return parsed severity level
     */
    static PostgresLogLevel parseLevel(final String line) {
        final Matcher matcher = SEVERITY_PATTERN.matcher(Objects.requireNonNull(line, "line"));
        final PostgresLogLevel level;
        if (matcher.find()) {
            // The pattern only captures keywords that match PostgresLogLevel constant names exactly.
            level = PostgresLogLevel.valueOf(matcher.group(1));
        } else {
            level = PostgresLogLevel.UNKNOWN;
        }

        return level;
    }
}
