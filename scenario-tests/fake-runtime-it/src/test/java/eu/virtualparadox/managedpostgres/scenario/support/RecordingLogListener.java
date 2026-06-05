package eu.virtualparadox.managedpostgres.scenario.support;

import eu.virtualparadox.managedpostgres.observe.PostgresLogLine;
import eu.virtualparadox.managedpostgres.observe.PostgresLogListener;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.LockSupport;

/**
 * Thread-safe {@link PostgresLogListener} that records every delivered {@link PostgresLogLine} for assertions.
 */
public final class RecordingLogListener implements PostgresLogListener {

    private final List<PostgresLogLine> lines = new CopyOnWriteArrayList<>();

    public RecordingLogListener() {}

    @Override
    public void onLogLine(final PostgresLogLine line) {
        lines.add(line);
    }

    /**
     * Returns an immutable snapshot of the recorded log lines in arrival order.
     *
     * @return recorded log lines
     */
    public List<PostgresLogLine> lines() {
        return List.copyOf(lines);
    }

    /**
     * Polls until at least one log line has been recorded or the bounded timeout elapses, keeping log-tail timing
     * deterministic for assertions.
     *
     * @param timeout maximum time to wait for the first log line
     */
    public void awaitAtLeastOneLine(final Duration timeout) {
        final long deadline = System.nanoTime() + timeout.toNanos();
        while (lines.isEmpty() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(Duration.ofMillis(25).toNanos());
        }
    }
}
