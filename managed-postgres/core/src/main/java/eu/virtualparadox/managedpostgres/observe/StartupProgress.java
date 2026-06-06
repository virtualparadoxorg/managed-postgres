package eu.virtualparadox.managedpostgres.observe;

import java.util.Objects;

/**
 * Immutable startup progress event delivered to a {@link ManagedPostgresProgressListener}.
 *
 * @param phase startup phase this event describes
 * @param completedBytes bytes processed so far (for byte-oriented phases such as downloading), or 0
 * @param totalBytes total bytes expected (for byte-oriented phases), or 0 when unknown
 * @param message human-readable description of the event
 */
public record StartupProgress(StartupPhase phase, long completedBytes, long totalBytes, String message) {

    /**
     * Creates an immutable startup progress event.
     *
     * @param phase startup phase this event describes
     * @param completedBytes bytes processed so far, or 0
     * @param totalBytes total bytes expected, or 0 when unknown
     * @param message human-readable description of the event
     */
    public StartupProgress {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(message, "message");
    }

    /**
     * Creates a phase-only progress event with no byte counters.
     *
     * @param phase startup phase this event describes
     * @param message human-readable description of the event
     * @return phase-only progress event
     */
    public static StartupProgress phase(final StartupPhase phase, final String message) {
        return new StartupProgress(phase, 0, 0, message);
    }

    /**
     * Returns the completion percentage for byte-oriented phases.
     *
     * @return percentage in {@code [0, 100]}, or {@code -1} when the total is unknown
     */
    public int percent() {
        return totalBytes > 0 ? (int) (completedBytes * 100L / totalBytes) : -1;
    }
}
