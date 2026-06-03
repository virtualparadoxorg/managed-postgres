package eu.virtualparadox.managedpostgres.lifecycle.cleanup;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.exception.PostgresDestroyException;
import eu.virtualparadox.managedpostgres.filesystem.DirectoryPublisher;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.lifecycle.layout.HeldPostgresLocks;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import eu.virtualparadox.managedpostgres.lifecycle.process.PostmasterPidFile;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.stop.PostgresStopCommand;
import eu.virtualparadox.managedpostgres.lifecycle.stop.PostgresStopCompatibility;
import eu.virtualparadox.managedpostgres.metadata.MetadataStore;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.runtime.DefaultRuntimeResolver;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Destroys persistent framework-managed PostgreSQL storage after compatibility and ownership checks.
 */
public final class DestroyManagedPostgresWorkflow {

    private final PostgresStopCommand stopCommand;
    private final FileSystemOperationJournal fileSystem;
    private final PostgresLockService lockService;

    /**
     * Creates a destroy workflow.
     */
    public DestroyManagedPostgresWorkflow() {
        this(
                new PostgresStopCommand(new DefaultRuntimeResolver(), Duration.ofSeconds(30)),
                new FileSystemOperationJournal(),
                new PostgresLockService());
    }

    DestroyManagedPostgresWorkflow(
            final PostgresStopCommand stopCommand,
            final FileSystemOperationJournal fileSystem,
            final PostgresLockService lockService) {
        this.stopCommand = Objects.requireNonNull(stopCommand, "stopCommand");
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        this.lockService = Objects.requireNonNull(lockService, "lockService");
    }

    /**
     * Destroys managed PostgreSQL cluster storage.
     *
     * @param configuration managed PostgreSQL configuration
     */
    public void destroyCluster(final ManagedPostgresConfiguration configuration) {
        final ManagedPostgresConfiguration checkedConfiguration =
                Objects.requireNonNull(configuration, "configuration");
        verifyPersistentStorage(checkedConfiguration);
        final PostgresLayout layout = PostgresLayout.plan(checkedConfiguration.storage(), fileSystem);
        if (Files.exists(layout.root())) {
            destroyExistingCluster(checkedConfiguration, layout);
        }
    }

    private void destroyExistingCluster(final ManagedPostgresConfiguration configuration, final PostgresLayout layout) {
        try (HeldPostgresLocks locks = lockService.acquireLifecycleLocks(layout)) {
            requireLocks(locks);
            destroyLocked(configuration, layout, new MetadataStore(layout.metadataPath(), fileSystem));
        } catch (final PostgresDestroyException exception) {
            throw exception;
        } catch (final ManagedPostgresException | UncheckedIOException exception) {
            throw new PostgresDestroyException(
                    "Managed PostgreSQL destroy failed",
                    exception,
                    CleanupWorkflowDiagnostics.destroy(
                            "storage-root", layout.root().toString()));
        }
    }

    private void destroyLocked(
            final ManagedPostgresConfiguration configuration,
            final PostgresLayout layout,
            final MetadataStore metadataStore) {
        final StartPostgresWorkflow.Configuration startConfiguration =
                new StartPostgresWorkflow.Configuration(configuration);
        final Optional<PostgresInstanceMetadata> metadata = metadataStore.read();
        if (metadata.isPresent()) {
            stopAndDelete(layout, startConfiguration, metadata.get());
        } else {
            destroyWithoutMetadata(layout);
        }
    }

    private void stopAndDelete(
            final PostgresLayout layout,
            final StartPostgresWorkflow.Configuration startConfiguration,
            final PostgresInstanceMetadata metadata) {
        new PostgresStopCompatibility().verify(startConfiguration, layout, metadata);
        stopCommand.stop(startConfiguration, layout);
        deleteRoot(layout);
    }

    private void destroyWithoutMetadata(final PostgresLayout layout) {
        verifyPidAbsence(layout);
        verifyFrameworkManaged(layout);
        deleteRoot(layout);
    }

    private static void verifyPersistentStorage(final ManagedPostgresConfiguration configuration) {
        if (configuration.storage().temporaryStorage()) {
            throw new PostgresDestroyException(
                    "Explicit destroy is unsupported for temporary cluster storage",
                    CleanupWorkflowDiagnostics.destroy(
                            "temporary-storage", configuration.storage().path().toString()));
        }
    }

    private static void verifyPidAbsence(final PostgresLayout layout) {
        if (PostmasterPidFile.readPid(layout.dataDirectory()).isPresent()) {
            throw new PostgresDestroyException(
                    "Managed PostgreSQL destroy refused because live PostgreSQL ownership could not be verified",
                    CleanupWorkflowDiagnostics.destroy(
                            "data-directory", layout.dataDirectory().toString()));
        }
    }

    private static void verifyFrameworkManaged(final PostgresLayout layout) {
        if (!looksFrameworkManaged(layout)) {
            throw new PostgresDestroyException(
                    "Managed PostgreSQL destroy refused because storage does not look framework-managed",
                    CleanupWorkflowDiagnostics.destroy(
                            "storage-root", layout.root().toString()));
        }
    }

    private static boolean looksFrameworkManaged(final PostgresLayout layout) {
        final PostgresLayout checkedLayout = Objects.requireNonNull(layout, "layout");
        return Files.isDirectory(checkedLayout.root())
                && Files.isDirectory(checkedLayout.dataDirectory())
                && Files.isDirectory(checkedLayout.stateDirectory())
                && Files.isDirectory(checkedLayout.locksDirectory());
    }

    private static void deleteRoot(final PostgresLayout layout) {
        DirectoryPublisher.deleteRecursivelyIfExists(layout.root());
    }

    private static void requireLocks(final HeldPostgresLocks locks) {
        Objects.requireNonNull(locks, "locks");
    }
}
