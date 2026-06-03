package eu.virtualparadox.managedpostgres.spring.boot4.bootstrap;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;

/**
 * Test support for controlling the static managed PostgreSQL bootstrap context.
 */
public final class ManagedPostgresBootstrapContextTestSupport {

    private ManagedPostgresBootstrapContextTestSupport() {}

    /**
     * Stores fake managed PostgreSQL handles in the bootstrap context.
     *
     * @param managedPostgres managed PostgreSQL lifecycle object
     * @param runningPostgres running PostgreSQL handle
     */
    public static void store(final ManagedPostgres managedPostgres, final RunningPostgres runningPostgres) {
        ManagedPostgresBootstrapContext.store(managedPostgres, runningPostgres);
    }

    /**
     * Stores fake managed PostgreSQL handles and bootstrap metrics in the bootstrap context.
     *
     * @param managedPostgres managed PostgreSQL lifecycle object
     * @param runningPostgres running PostgreSQL handle
     * @param metrics bootstrap metrics snapshot
     */
    public static void store(
            final ManagedPostgres managedPostgres,
            final RunningPostgres runningPostgres,
            final ManagedPostgresBootstrapMetrics metrics) {
        ManagedPostgresBootstrapContext.store(managedPostgres, runningPostgres, metrics);
    }

    /**
     * Resets the bootstrap context.
     */
    public static void reset() {
        ManagedPostgresBootstrapContext.reset();
    }
}
