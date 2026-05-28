package eu.virtualparadox.managedpostgres.lifecycle.start;

import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.psql.CommandRunnerPsqlBootstrapClient;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.psql.PsqlBootstrapClient;
import eu.virtualparadox.managedpostgres.lifecycle.psql.PsqlBootstrapCommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.psql.PsqlBootstrapDiagnostics;
import eu.virtualparadox.managedpostgres.lifecycle.psql.PsqlCommandFactory;
import eu.virtualparadox.managedpostgres.lifecycle.psql.PsqlScriptFileStore;

/**
 * Coordinates postgres bootstrap service factory behavior for managed PostgreSQL internals.
 */
public final class PostgresBootstrapServiceFactory {

    private final ManagedFileSystem fileSystem;
    private final CommandRunner commandRunner;
    private final Duration timeout;

    /**
     * Creates a PostgresBootstrapServiceFactory instance.
     *
     * @param fileSystem file system value
     * @param commandRunner command runner value
     * @param timeout timeout value
     */
    public PostgresBootstrapServiceFactory(
            final ManagedFileSystem fileSystem,
            final CommandRunner commandRunner,
            final Duration timeout) {
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * Returns the create result.
     *
     * @param runtimeDirectory runtime directory value
     * @param layout layout value
     * @param postgresqlVersion PostgreSQL version value
     * @return create result
     */
    public PostgresBootstrapService create(
            final Path runtimeDirectory,
            final PostgresLayout layout,
            final String postgresqlVersion) {
        final PsqlBootstrapDiagnostics diagnostics = new PsqlBootstrapDiagnostics();
        final PsqlBootstrapClient bootstrapClient = new CommandRunnerPsqlBootstrapClient(
                new PsqlCommandFactory(runtimeDirectory, timeout),
                new PsqlBootstrapCommandRunner(commandRunner, diagnostics),
                new PsqlScriptFileStore(layout.stateDirectory().resolve("bootstrap-sql"), fileSystem, diagnostics));

        return new PostgresBootstrapService(bootstrapClient, runtimeDirectory, postgresqlVersion);
    }
}
