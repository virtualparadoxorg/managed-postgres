package eu.virtualparadox.managedpostgres.lifecycle.restore.pgrestore;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import java.nio.file.Path;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRequest;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreDiagnostics;

/**
 * Runs {@code pg_restore} and converts command failures into restore exceptions.
 */
public final class PgRestoreCommandExecutor {

    private final PgRestoreCommandFactory commandFactory;
    private final CommandRunner commandRunner;
    private final PostgresConnectionInfo connectionInfo;
    private final PostgresRestoreDiagnostics diagnostics;

    /**
     * Creates a PgRestoreCommandExecutor instance.
     *
     * @param commandFactory command factory value
     * @param commandRunner command runner value
     * @param connectionInfo connection info value
     * @param diagnostics diagnostics value
     */
    public PgRestoreCommandExecutor(
            final PgRestoreCommandFactory commandFactory,
            final CommandRunner commandRunner,
            final PostgresConnectionInfo connectionInfo,
            final PostgresRestoreDiagnostics diagnostics) {
        this.commandFactory = Objects.requireNonNull(commandFactory, "commandFactory");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
        this.connectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    /**
     * Performs the restore from operation.
     *
     * @param backup backup value
     */
    public void restoreFrom(final Path backup) {
        final CommandRequest request = commandFactory.customRestore(connectionInfo, backup);
        final CommandResult result;
        try {
            result = commandRunner.run(request);
        } catch (final ManagedPostgresException exception) {
            throw new PostgresRestoreException(
                    "PostgreSQL pg_restore command failed",
                    exception,
                    diagnostics.commandRunnerFailure(
                            Objects.toString(exception.getMessage(), exception.getClass().getName()),
                            connectionInfo));
        }
        if (!result.successful()) {
            throw new PostgresRestoreException(
                    "PostgreSQL pg_restore command failed",
                    diagnostics.commandFailure(result, connectionInfo));
        }
    }
}
