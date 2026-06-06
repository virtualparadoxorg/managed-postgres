package eu.virtualparadox.managedpostgres.lifecycle.restore.pgrestore;

import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import eu.virtualparadox.managedpostgres.lifecycle.ManagedPostgresFrameworkVersion;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupManifestSource;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperationContext;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreOperation;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreOperationProvider;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Creates {@code pg_restore}-backed logical restore operations.
 */
public final class PgRestoreOperationProvider implements PostgresRestoreOperationProvider {

    private final PgRestoreOperationProviderDependencies dependencies;

    /**
     * Returns the create result.
     *
     * @param fileSystem file system value
     * @param lockService lock service value
     * @param timeout timeout value
     * @param commandRunner command runner value
     * @return create result
     */
    public static PgRestoreOperationProvider create(
            final ManagedFileSystem fileSystem,
            final PostgresLockService lockService,
            final Duration timeout,
            final CommandRunner commandRunner) {
        return new PgRestoreOperationProvider(new PgRestoreOperationProviderDependencies(
                commandRunner,
                fileSystem,
                lockService,
                timeout,
                Clock.systemUTC(),
                ManagedPostgresFrameworkVersion.current()));
    }

    /**
     * Creates a PgRestoreOperationProvider instance.
     *
     * @param dependencies dependencies value
     */
    public PgRestoreOperationProvider(final PgRestoreOperationProviderDependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PostgresRestoreOperation create(final PostgresBackupOperationContext context) {
        final PostgresBackupOperationContext checkedContext = Objects.requireNonNull(context, "context");

        return PgRestoreServiceFactory.create(new PgRestoreDependencies(
                checkedContext.layout(),
                checkedContext.runtimeDirectory(),
                dependencies.commandRunner(),
                dependencies.fileSystem(),
                dependencies.lockService(),
                dependencies.timeout(),
                manifestSource(checkedContext)));
    }

    private BackupManifestSource manifestSource(final PostgresBackupOperationContext context) {
        return new BackupManifestSource(
                context.connectionInfo(), context.metadata(), dependencies.clock(), dependencies.frameworkVersion());
    }
}
