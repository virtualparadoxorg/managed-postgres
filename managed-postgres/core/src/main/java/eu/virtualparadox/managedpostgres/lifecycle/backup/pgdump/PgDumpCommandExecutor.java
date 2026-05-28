package eu.virtualparadox.managedpostgres.lifecycle.backup.pgdump;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.exception.PostgresBackupException;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import java.nio.file.Path;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRequest;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.backup.PostgresBackupDiagnostics;

/**
 * Runs pg_dump and converts command failures into backup exceptions.
 */
public final class PgDumpCommandExecutor {

    private final PgDumpCommandFactory commandFactory;
    private final CommandRunner commandRunner;
    private final PostgresConnectionInfo connectionInfo;
    private final PostgresBackupDiagnostics diagnostics;

    /**
     * Creates a PgDumpCommandExecutor instance.
     *
     * @param commandFactory command factory value
     * @param commandRunner command runner value
     * @param connectionInfo connection info value
     * @param diagnostics diagnostics value
     */
    public PgDumpCommandExecutor(
            final PgDumpCommandFactory commandFactory,
            final CommandRunner commandRunner,
            final PostgresConnectionInfo connectionInfo,
            final PostgresBackupDiagnostics diagnostics) {
        this.commandFactory = Objects.requireNonNull(commandFactory, "commandFactory");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
        this.connectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    /**
     * Performs the dump to operation.
     *
     * @param stagedBackup staged backup value
     */
    public void dumpTo(final Path stagedBackup) {
        final CommandRequest request = commandFactory.customDump(connectionInfo, stagedBackup);
        final CommandResult result;
        try {
            result = commandRunner.run(request);
        } catch (final ManagedPostgresException exception) {
            throw new PostgresBackupException(
                    "PostgreSQL pg_dump command failed",
                    exception,
                    diagnostics.commandRunnerFailure(
                            Objects.toString(exception.getMessage(), exception.getClass().getName()),
                            connectionInfo));
        }
        if (!result.successful()) {
            throw new PostgresBackupException(
                    "PostgreSQL pg_dump command failed",
                    diagnostics.commandFailure(result, connectionInfo));
        }
    }
}
