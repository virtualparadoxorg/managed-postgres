package eu.virtualparadox.managedpostgres.lifecycle.log;

import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sink that forwards each PostgreSQL log line to every delegate sink in order.
 */
@SuppressWarnings({
    // A delegate sink wraps untrusted user code (e.g. a PostgresLogListener); catching RuntimeException is
    // intentional so one misbehaving delegate cannot starve the other sinks.
    "PMD.AvoidCatchingGenericException"
})
public final class CompositePostgresLogSink implements PostgresLogSink {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompositePostgresLogSink.class);

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
            try {
                delegate.log(loggerName, line);
            } catch (final RuntimeException exception) {
                LOGGER.warn("managed PostgreSQL log delegate threw while forwarding a line; continuing", exception);
            }
        }
    }
}
