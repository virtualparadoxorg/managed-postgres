package eu.virtualparadox.managedpostgres.internal;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import eu.virtualparadox.managedpostgres.lifecycle.ManagedPostgresService;
import java.util.Objects;

/**
 * Coordinates configured managed postgres behavior for managed PostgreSQL internals.
 */
public final class ConfiguredManagedPostgres implements ManagedPostgres {

    private final ManagedPostgresConfiguration configuration;
    private final ManagedPostgresService service;
    private final ManagedPostgresObservers observers;

    /**
     * Creates a ConfiguredManagedPostgres instance.
     *
     * @param configuration configuration value
     */
    public ConfiguredManagedPostgres(final ManagedPostgresConfiguration configuration) {
        this(configuration, new ManagedPostgresService(), ManagedPostgresObservers.defaults());
    }

    /**
     * Creates a ConfiguredManagedPostgres instance.
     *
     * @param configuration configuration value
     * @param observers startup observers value
     */
    public ConfiguredManagedPostgres(
            final ManagedPostgresConfiguration configuration, final ManagedPostgresObservers observers) {
        this(configuration, new ManagedPostgresService(), observers);
    }

    /**
     * Creates a ConfiguredManagedPostgres instance.
     *
     * @param configuration configuration value
     * @param service service value
     */
    public ConfiguredManagedPostgres(
            final ManagedPostgresConfiguration configuration, final ManagedPostgresService service) {
        this(configuration, service, ManagedPostgresObservers.defaults());
    }

    /**
     * Creates a ConfiguredManagedPostgres instance.
     *
     * @param configuration configuration value
     * @param service service value
     * @param observers startup observers value
     */
    public ConfiguredManagedPostgres(
            final ManagedPostgresConfiguration configuration,
            final ManagedPostgresService service,
            final ManagedPostgresObservers observers) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.service = Objects.requireNonNull(service, "service");
        this.observers = Objects.requireNonNull(observers, "observers");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RunningPostgres start() {
        return service.start(configuration, observers);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PostgresStatus status() {
        return doctor().status();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DoctorReport doctor() {
        return service.doctor(configuration);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        service.stop(configuration);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup() {
        service.cleanup(configuration);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroyCluster() {
        service.destroyCluster(configuration);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        stop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "ConfiguredManagedPostgres[configuration=%s]".formatted(configuration);
    }
}
