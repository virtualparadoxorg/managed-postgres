package eu.virtualparadox.managedpostgres.scenario.support;

import eu.virtualparadox.managedpostgres.observe.ManagedPostgresProgressListener;
import eu.virtualparadox.managedpostgres.observe.StartupPhase;
import eu.virtualparadox.managedpostgres.observe.StartupProgress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe {@link ManagedPostgresProgressListener} that records every {@link StartupProgress} event for assertions.
 */
public final class RecordingProgressListener implements ManagedPostgresProgressListener {

    private final List<StartupProgress> events = new CopyOnWriteArrayList<>();

    public RecordingProgressListener() {}

    @Override
    public void onProgress(final StartupProgress progress) {
        events.add(progress);
    }

    /**
     * Returns an immutable snapshot of the recorded events in arrival order.
     *
     * @return recorded startup progress events
     */
    public List<StartupProgress> events() {
        return List.copyOf(events);
    }

    /**
     * Returns the recorded phases in arrival order.
     *
     * @return recorded startup phases
     */
    public List<StartupPhase> phases() {
        return events().stream().map(StartupProgress::phase).toList();
    }
}
