package eu.virtualparadox.managedpostgres.internal;

import eu.virtualparadox.managedpostgres.observe.ManagedPostgresProgressListener;
import eu.virtualparadox.managedpostgres.observe.PostgresLogListener;
import java.util.Objects;

/**
 * Immutable holder for the startup observers (progress + log listeners) threaded from the builder
 * down to the start workflow.
 *
 * @param progress startup progress listener
 * @param log PostgreSQL server log listener
 */
public record ManagedPostgresObservers(ManagedPostgresProgressListener progress, PostgresLogListener log) {

    /**
     * Creates an immutable observers holder.
     *
     * @param progress startup progress listener
     * @param log PostgreSQL server log listener
     */
    public ManagedPostgresObservers {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(log, "log");
    }

    /**
     * Returns the default observers: SLF4J progress logging and a no-op log listener.
     *
     * @return default observers
     */
    public static ManagedPostgresObservers defaults() {
        return new ManagedPostgresObservers(ManagedPostgresProgressListener.slf4j(), PostgresLogListener.none());
    }

    /**
     * Returns a copy with the progress listener replaced.
     *
     * @param newProgress replacement progress listener
     * @return updated observers
     */
    public ManagedPostgresObservers withProgress(final ManagedPostgresProgressListener newProgress) {
        return new ManagedPostgresObservers(Objects.requireNonNull(newProgress, "newProgress"), log);
    }

    /**
     * Returns a copy with the log listener replaced.
     *
     * @param newLog replacement log listener
     * @return updated observers
     */
    public ManagedPostgresObservers withLog(final PostgresLogListener newLog) {
        return new ManagedPostgresObservers(progress, Objects.requireNonNull(newLog, "newLog"));
    }
}
