package eu.virtualparadox.managedpostgres.lifecycle.stop;

import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import eu.virtualparadox.managedpostgres.metadata.MetadataStore;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.layout.HeldPostgresLocks;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;

/**
 * Stops a configured managed PostgreSQL instance from persisted metadata.
 */
public final class StopPostgresWorkflow {

    private final PostgresStopCommand stopCommand;
    private final ManagedFileSystem fileSystem;
    private final PostgresLockService lockService;
    private final PostgresStopMetadataReader metadataReader;
    private final PostgresStopCompatibility compatibility;

    /**
     * Creates a StopPostgresWorkflow instance.
     *
     * @param stopCommand stop command value
     * @param fileSystem file system value
     * @param lockService lock service value
     */
    public StopPostgresWorkflow(
            final PostgresStopCommand stopCommand,
            final ManagedFileSystem fileSystem,
            final PostgresLockService lockService) {
        this.stopCommand = Objects.requireNonNull(stopCommand, "stopCommand");
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        this.lockService = Objects.requireNonNull(lockService, "lockService");
        metadataReader = new PostgresStopMetadataReader();
        compatibility = new PostgresStopCompatibility();
    }

    /**
     * Performs the stop operation.
     *
     * @param configuration configuration value
     */
    public void stop(final ManagedPostgresConfiguration configuration) {
        stop(new StartPostgresWorkflow.Configuration(Objects.requireNonNull(configuration, "configuration")));
    }

    /**
     * Performs the stop operation.
     *
     * @param configuration configuration value
     */
    public void stop(final StartPostgresWorkflow.Configuration configuration) {
        final StartPostgresWorkflow.Configuration checkedConfiguration =
                Objects.requireNonNull(configuration, "configuration");
        final PostgresLayout layout = PostgresLayout.plan(checkedConfiguration.storage(), fileSystem);
        final MetadataStore metadataStore = new MetadataStore(layout.metadataPath(), fileSystem);
        metadataReader.read(metadataStore)
                .ifPresent(metadata -> {
                    try (HeldPostgresLocks locks = lockService.acquireLifecycleLocks(layout)) {
                        requireHeldLocks(locks);
                        compatibility.verify(checkedConfiguration, layout, metadata);
                        stopCommand.stop(checkedConfiguration, layout);
                        if (checkedConfiguration.stableRandomPortSelection()) {
                            metadataStore.writePortReservation(checkedConfiguration.name(), metadata.port());
                        } else {
                            metadataStore.delete();
                        }
                    }
                });
    }

    private static void requireHeldLocks(final HeldPostgresLocks locks) {
        Objects.requireNonNull(locks, "locks");
    }
}
