package eu.virtualparadox.managedpostgres.lifecycle;

import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.lifecycle.cleanup.CleanupManagedPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.cleanup.DestroyManagedPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorService;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.stop.PostgresStopCommand;
import eu.virtualparadox.managedpostgres.lifecycle.stop.StopPostgresWorkflow;
import eu.virtualparadox.managedpostgres.runtime.DefaultRuntimeResolver;
import java.time.Duration;

/**
 * Builds default lifecycle collaborators for the service facade.
 */
public final class ManagedPostgresLifecycleFactory {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private ManagedPostgresLifecycleFactory() {}

    /**
     * Returns the start workflow result.
     *
     * @return start workflow result
     */
    public static StartPostgresWorkflow startWorkflow() {
        return new StartPostgresWorkflow(
                new DefaultRuntimeResolver(),
                new FileSystemOperationJournal(),
                new PostgresLockService(),
                DEFAULT_TIMEOUT);
    }

    /**
     * Returns the stop workflow result.
     *
     * @return stop workflow result
     */
    public static StopPostgresWorkflow stopWorkflow() {
        return new StopPostgresWorkflow(
                new PostgresStopCommand(new DefaultRuntimeResolver(), DEFAULT_TIMEOUT),
                new FileSystemOperationJournal(),
                new PostgresLockService());
    }

    /**
     * Returns the doctor service result.
     *
     * @return doctor service result
     */
    public static DoctorService doctorService() {
        return new DoctorService(new FileSystemOperationJournal());
    }

    /**
     * Returns the cleanup workflow result.
     *
     * @return cleanup workflow result
     */
    public static CleanupManagedPostgresWorkflow cleanupWorkflow() {
        return new CleanupManagedPostgresWorkflow();
    }

    /**
     * Returns the destroy workflow result.
     *
     * @return destroy workflow result
     */
    public static DestroyManagedPostgresWorkflow destroyWorkflow() {
        return new DestroyManagedPostgresWorkflow();
    }
}
