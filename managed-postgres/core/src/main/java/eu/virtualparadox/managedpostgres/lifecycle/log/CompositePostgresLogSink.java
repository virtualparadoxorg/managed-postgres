package eu.virtualparadox.managedpostgres.lifecycle.log;

import java.util.List;
import java.util.Objects;

/**
 * Sink that forwards each PostgreSQL log line to every delegate sink in order.
 */
public final class CompositePostgresLogSink implements PostgresLogSink {

    private final List<PostgresLogSink> delegates;

    /**
     * Creates a composite sink.
     *
     * @param delegates delegate sinks receiving each forwarded line
     */
    public CompositePostgresLogSink(final List<PostgresLogSink> delegates) {
        this.delegates = List.copyOf(Objects.requireNonNull(delegates, "delegates")).stream()
                .map(delegate -> Objects.requireNonNull(delegate, "delegate"))
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void log(final String loggerName, final String line) {
        Objects.requireNonNull(loggerName, "loggerName");
        Objects.requireNonNull(line, "line");
        for (final PostgresLogSink delegate : delegates) {
            delegate.log(loggerName, line);
        }
    }
}
