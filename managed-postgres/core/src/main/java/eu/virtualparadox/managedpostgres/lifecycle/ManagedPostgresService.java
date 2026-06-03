package eu.virtualparadox.managedpostgres.lifecycle;

import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import eu.virtualparadox.managedpostgres.lifecycle.cleanup.CleanupManagedPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.cleanup.DestroyManagedPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorService;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.stop.StopPostgresWorkflow;
import java.util.Objects;

/**
 * Internal service facade for managed PostgreSQL lifecycle workflows.
 */
public final class ManagedPostgresService {

    private final StartPostgresWorkflow startWorkflow;
    private final StopPostgresWorkflow stopWorkflow;
    private final DoctorService doctorService;
    private final CleanupManagedPostgresWorkflow cleanupWorkflow;
    private final DestroyManagedPostgresWorkflow destroyWorkflow;

    /**
     * Creates a lifecycle service with default collaborators.
     */
    public ManagedPostgresService() {
        this(
                ManagedPostgresLifecycleFactory.startWorkflow(),
                ManagedPostgresLifecycleFactory.stopWorkflow(),
                ManagedPostgresLifecycleFactory.doctorService(),
                ManagedPostgresLifecycleFactory.cleanupWorkflow(),
                ManagedPostgresLifecycleFactory.destroyWorkflow());
    }

    /**
     * Creates a ManagedPostgresService instance.
     *
     * @param startWorkflow start workflow value
     */
    public ManagedPostgresService(final StartPostgresWorkflow startWorkflow) {
        this(startWorkflow, ManagedPostgresLifecycleFactory.stopWorkflow());
    }

    /**
     * Creates a ManagedPostgresService instance.
     *
     * @param startWorkflow start workflow value
     * @param stopWorkflow stop workflow value
     */
    public ManagedPostgresService(final StartPostgresWorkflow startWorkflow, final StopPostgresWorkflow stopWorkflow) {
        this(
                startWorkflow,
                stopWorkflow,
                ManagedPostgresLifecycleFactory.doctorService(),
                ManagedPostgresLifecycleFactory.cleanupWorkflow(),
                ManagedPostgresLifecycleFactory.destroyWorkflow());
    }

    /**
     * Creates a ManagedPostgresService instance.
     *
     * @param startWorkflow start workflow value
     * @param stopWorkflow stop workflow value
     * @param doctorService doctor service value
     * @param cleanupWorkflow cleanup workflow value
     * @param destroyWorkflow destroy workflow value
     */
    public ManagedPostgresService(
            final StartPostgresWorkflow startWorkflow,
            final StopPostgresWorkflow stopWorkflow,
            final DoctorService doctorService,
            final CleanupManagedPostgresWorkflow cleanupWorkflow,
            final DestroyManagedPostgresWorkflow destroyWorkflow) {
        this.startWorkflow = Objects.requireNonNull(startWorkflow, "startWorkflow");
        this.stopWorkflow = Objects.requireNonNull(stopWorkflow, "stopWorkflow");
        this.doctorService = Objects.requireNonNull(doctorService, "doctorService");
        this.cleanupWorkflow = Objects.requireNonNull(cleanupWorkflow, "cleanupWorkflow");
        this.destroyWorkflow = Objects.requireNonNull(destroyWorkflow, "destroyWorkflow");
    }

    /**
     * Starts a managed PostgreSQL instance.
     *
     * @param configuration managed PostgreSQL configuration
     * @return running PostgreSQL handle
     */
    public RunningPostgres start(final ManagedPostgresConfiguration configuration) {
        return start(new StartPostgresWorkflow.Configuration(configuration));
    }

    /**
     * Starts a managed PostgreSQL instance.
     *
     * @param configuration startup configuration
     * @return running PostgreSQL handle
     */
    public RunningPostgres start(final StartPostgresWorkflow.Configuration configuration) {
        return startWorkflow.start(configuration);
    }

    /**
     * Runs non-mutating diagnostics for a managed PostgreSQL configuration.
     *
     * @param configuration managed PostgreSQL configuration
     * @return doctor report
     */
    public DoctorReport doctor(final ManagedPostgresConfiguration configuration) {
        return doctorService.doctor(configuration);
    }

    /**
     * Stops a configured managed PostgreSQL instance.
     *
     * @param configuration managed PostgreSQL configuration
     */
    public void stop(final ManagedPostgresConfiguration configuration) {
        stopWorkflow.stop(configuration);
    }

    /**
     * Runs a non-destructive cleanup pass for a managed PostgreSQL configuration.
     *
     * @param configuration managed PostgreSQL configuration
     */
    public void cleanup(final ManagedPostgresConfiguration configuration) {
        cleanupWorkflow.cleanup(configuration);
    }

    /**
     * Destroys a managed PostgreSQL cluster explicitly.
     *
     * @param configuration managed PostgreSQL configuration
     */
    public void destroyCluster(final ManagedPostgresConfiguration configuration) {
        destroyWorkflow.destroyCluster(configuration);
    }
}
