package eu.virtualparadox.managedpostgres.spring.common.bootstrap;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stores the managed PostgreSQL handles started during the Spring Boot environment phase.
 */
public final class ManagedPostgresBootstrapContext {

    /**
     * Internal Spring bean name used to carry the context-specific bootstrap handles.
     */
    public static final String BEAN_NAME = "managedPostgresBootstrapContext";

    private static final ManagedPostgresBootstrapContext EMPTY =
            new ManagedPostgresBootstrapContext(Optional.empty(), Optional.empty(), Optional.empty());
    private static final AtomicReference<ManagedPostgresBootstrapContext> CURRENT = new AtomicReference<>(EMPTY);

    private final Optional<ManagedPostgres> managedPostgres;
    private final Optional<RunningPostgres> runningPostgres;
    private final Optional<ManagedPostgresBootstrapMetrics> metrics;

    private ManagedPostgresBootstrapContext(
            final Optional<ManagedPostgres> managedPostgres,
            final Optional<RunningPostgres> runningPostgres,
            final Optional<ManagedPostgresBootstrapMetrics> metrics) {
        this.managedPostgres = Objects.requireNonNull(managedPostgres, "managedPostgres");
        this.runningPostgres = Objects.requireNonNull(runningPostgres, "runningPostgres");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Returns the current managed PostgreSQL bootstrap context.
     *
     * @return current bootstrap context
     */
    public static ManagedPostgresBootstrapContext current() {
        return Objects.requireNonNull(CURRENT.get(), "current");
    }

    /**
     * Returns the managed PostgreSQL lifecycle object started during bootstrap, when present.
     *
     * @return managed PostgreSQL lifecycle object
     */
    public Optional<ManagedPostgres> managedPostgres() {
        return managedPostgres;
    }

    /**
     * Returns the running PostgreSQL handle started during bootstrap, when present.
     *
     * @return running PostgreSQL handle
     */
    public Optional<RunningPostgres> runningPostgres() {
        return runningPostgres;
    }

    /**
     * Returns optional bootstrap metrics captured during early Spring Boot startup.
     *
     * @return bootstrap metrics snapshot
     */
    public Optional<ManagedPostgresBootstrapMetrics> metrics() {
        return metrics;
    }

    static ManagedPostgresBootstrapContext of(
            final ManagedPostgres managedPostgres, final RunningPostgres runningPostgres) {
        return of(
                managedPostgres, runningPostgres, new ManagedPostgresBootstrapMetrics(Duration.ZERO, Duration.ZERO, 0));
    }

    static ManagedPostgresBootstrapContext of(
            final ManagedPostgres managedPostgres,
            final RunningPostgres runningPostgres,
            final ManagedPostgresBootstrapMetrics metrics) {
        return new ManagedPostgresBootstrapContext(
                Optional.of(Objects.requireNonNull(managedPostgres, "managedPostgres")),
                Optional.of(Objects.requireNonNull(runningPostgres, "runningPostgres")),
                Optional.of(Objects.requireNonNull(metrics, "metrics")));
    }

    static void store(final ManagedPostgres managedPostgres, final RunningPostgres runningPostgres) {
        store(of(managedPostgres, runningPostgres));
    }

    static void store(
            final ManagedPostgres managedPostgres,
            final RunningPostgres runningPostgres,
            final ManagedPostgresBootstrapMetrics metrics) {
        store(of(managedPostgres, runningPostgres, metrics));
    }

    static void store(final ManagedPostgresBootstrapContext bootstrapContext) {
        CURRENT.set(Objects.requireNonNull(bootstrapContext, "bootstrapContext"));
    }

    static void reset() {
        CURRENT.set(EMPTY);
    }
}
