package eu.virtualparadox.managedpostgres;

import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import eu.virtualparadox.managedpostgres.dsl.ManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.internal.ExternalManagedPostgres;

/**
 * Managed PostgreSQL lifecycle entry point.
 */
public interface ManagedPostgres extends AutoCloseable {

    /**
     * Starts the configured PostgreSQL instance.
     *
     * @return running PostgreSQL handle
     */
    public RunningPostgres start();

    /**
     * Returns the current lifecycle status.
     *
     * @return current lifecycle status
     */
    public PostgresStatus status();

    /**
     * Runs non-mutating diagnostics for this managed PostgreSQL configuration.
     *
     * @return doctor report
     */
    public DoctorReport doctor();

    /**
     * Stops the managed PostgreSQL instance.
     */
    public void stop();

    /**
     * Runs a non-destructive cleanup pass for managed PostgreSQL artifacts.
     */
    public void cleanup();

    /**
     * Destroys the managed PostgreSQL cluster storage explicitly.
     */
    public void destroyCluster();

    /**
     * Closes this managed PostgreSQL instance.
     */
    @Override
    public void close();

    /**
     * Starts the fluent managed PostgreSQL configuration DSL.
     *
     * @return managed PostgreSQL builder
     */
    public static ManagedPostgresBuilder create() {
        return ManagedPostgresBuilder.local();
    }

    /**
     * Creates a persistent local PostgreSQL builder.
     *
     * @return persistent local builder
     */
    public static ManagedPostgresBuilder local() {
        return ManagedPostgresBuilder.local();
    }

    /**
     * Creates a temporary PostgreSQL builder.
     *
     * @return temporary builder
     */
    public static ManagedPostgresBuilder temporary() {
        return ManagedPostgresBuilder.temporary();
    }

    /**
     * Creates a validation-only facade for an externally managed PostgreSQL connection.
     *
     * @param connectionInfo connection details to validate
     * @return validation-only managed PostgreSQL facade
     */
    public static ManagedPostgres external(final PostgresConnectionInfo connectionInfo) {
        return new ExternalManagedPostgres(connectionInfo);
    }
}
