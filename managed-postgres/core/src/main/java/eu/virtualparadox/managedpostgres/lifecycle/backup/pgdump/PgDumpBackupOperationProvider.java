package eu.virtualparadox.managedpostgres.lifecycle.backup.pgdump;

import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupManifestSource;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperation;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperationContext;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperationProvider;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import eu.virtualparadox.managedpostgres.lifecycle.ManagedPostgresFrameworkVersion;

/**
 * Creates {@code pg_dump}-backed logical backup operations.
 */
public final class PgDumpBackupOperationProvider implements PostgresBackupOperationProvider {

    private final PgDumpBackupOperationProviderDependencies dependencies;

    /**
     * Returns the create result.
     *
     * @param fileSystem file system value
     * @param lockService lock service value
     * @param timeout timeout value
     * @param commandRunner command runner value
     * @return create result
     */
    public static PgDumpBackupOperationProvider create(
            final ManagedFileSystem fileSystem,
            final PostgresLockService lockService,
            final Duration timeout,
            final CommandRunner commandRunner) {
        return new PgDumpBackupOperationProvider(new PgDumpBackupOperationProviderDependencies(
                commandRunner,
                fileSystem,
                lockService,
                timeout,
                Clock.systemUTC(),
                ManagedPostgresFrameworkVersion.current()));
    }

    /**
     * Creates a PgDumpBackupOperationProvider instance.
     *
     * @param dependencies dependencies value
     */
    public PgDumpBackupOperationProvider(final PgDumpBackupOperationProviderDependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PostgresBackupOperation create(final PostgresBackupOperationContext context) {
        final PostgresBackupOperationContext checkedContext = Objects.requireNonNull(context, "context");

        return PgDumpBackupServiceFactory.create(new PgDumpBackupDependencies(
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
                context.connectionInfo(),
                context.metadata(),
                dependencies.clock(),
                dependencies.frameworkVersion());
    }
}
