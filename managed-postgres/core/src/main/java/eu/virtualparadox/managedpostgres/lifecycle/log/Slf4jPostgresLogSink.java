package eu.virtualparadox.managedpostgres.lifecycle.log;

import java.util.Objects;
import org.slf4j.LoggerFactory;

/**
 * SLF4J-backed sink for PostgreSQL process log lines.
 */
public final class Slf4jPostgresLogSink implements PostgresLogSink {

    /**
     * Creates an SLF4J-backed PostgreSQL log sink.
     */
    public Slf4jPostgresLogSink() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void log(final String loggerName, final String line) {
        LoggerFactory.getLogger(Objects.requireNonNull(loggerName, "loggerName"))
                .info(sanitize(Objects.requireNonNull(line, "line")));
    }

    private static String sanitize(final String line) {
        return line.replace('\n', ' ').replace('\r', ' ');
    }
}
