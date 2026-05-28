package eu.virtualparadox.managedpostgres.lifecycle.cleanup;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.exception.PostgresCleanupException;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.lifecycle.layout.HeldPostgresLocks;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import eu.virtualparadox.managedpostgres.lifecycle.log.PostgresLogRetention;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheCleaner;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Performs conservative cleanup for framework-owned cluster staging, logs, and runtime cache artifacts.
 */
public final class CleanupManagedPostgresWorkflow {

    private static final String POSTGRES_LOG = "postgres.log";

    private final FileSystemOperationJournal fileSystem;
    private final PostgresLockService lockService;
    private final RuntimeCacheCleaner runtimeCacheCleaner;
    private final PostgresLogRetention logRetention;
    private final CleanupRuntimeCacheLocator runtimeCacheLocator;

    /**
     * Creates a cleanup workflow.
     */
    public CleanupManagedPostgresWorkflow() {
        this(
                new FileSystemOperationJournal(),
                new PostgresLockService(),
                new RuntimeCacheCleaner(),
                new PostgresLogRetention(),
                new CleanupRuntimeCacheLocator());
    }

    CleanupManagedPostgresWorkflow(
            final FileSystemOperationJournal fileSystem,
            final PostgresLockService lockService,
            final RuntimeCacheCleaner runtimeCacheCleaner,
            final PostgresLogRetention logRetention,
            final CleanupRuntimeCacheLocator runtimeCacheLocator) {
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        this.lockService = Objects.requireNonNull(lockService, "lockService");
        this.runtimeCacheCleaner = Objects.requireNonNull(runtimeCacheCleaner, "runtimeCacheCleaner");
        this.logRetention = Objects.requireNonNull(logRetention, "logRetention");
        this.runtimeCacheLocator = Objects.requireNonNull(runtimeCacheLocator, "runtimeCacheLocator");
    }

    /**
     * Performs managed PostgreSQL cleanup.
     *
     * @param configuration managed PostgreSQL configuration
     */
    public void cleanup(final ManagedPostgresConfiguration configuration) {
        final ManagedPostgresConfiguration checkedConfiguration =
                Objects.requireNonNull(configuration, "configuration");
        if (checkedConfiguration.storage().temporaryStorage()) {
            throw new PostgresCleanupException(
                    "Explicit cleanup is unsupported for temporary cluster storage",
                    CleanupWorkflowDiagnostics.cleanup("temporary-storage", checkedConfiguration.storage().path().toString()));
        }

        final PostgresLayout layout = PostgresLayout.plan(checkedConfiguration.storage(), fileSystem);
        try (HeldPostgresLocks locks = lockService.acquireLifecycleLocks(layout)) {
            requireLocks(locks);
            recoverClusterStaging(layout);
            logRetention.trimHistory(
                    layout.stateDirectory().resolve(POSTGRES_LOG),
                    checkedConfiguration.cleanupPolicy().retainedLogFiles());
            runtimeCacheLocator.locate(configuration.runtimeSource()).ifPresent(runtimeCacheCleaner::clean);
        } catch (final PostgresCleanupException exception) {
            throw exception;
        } catch (final ManagedPostgresException | UncheckedIOException exception) {
            throw new PostgresCleanupException(
                    "Managed PostgreSQL cleanup failed",
                    exception,
                    CleanupWorkflowDiagnostics.cleanup("cleanup-root", layout.root().toString()));
        }
    }

    private void recoverClusterStaging(final PostgresLayout layout) {
        fileSystem.recover(layout.root());
        fileSystem.recover(layout.dataDirectory());
        fileSystem.recover(layout.stateDirectory());
    }

    private static void requireLocks(final HeldPostgresLocks locks) {
        Objects.requireNonNull(locks, "locks");
    }
}
