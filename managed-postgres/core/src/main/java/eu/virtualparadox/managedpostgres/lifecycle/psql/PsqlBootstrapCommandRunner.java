package eu.virtualparadox.managedpostgres.lifecycle.psql;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.exception.PostgresStartupException;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRequest;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;

/**
 * Coordinates psql bootstrap command runner behavior for managed PostgreSQL internals.
 */
public final class PsqlBootstrapCommandRunner {

    private final CommandRunner commandRunner;
    private final PsqlBootstrapDiagnostics diagnostics;

    /**
     * Creates a PsqlBootstrapCommandRunner instance.
     *
     * @param commandRunner command runner value
     * @param diagnostics diagnostics value
     */
    public PsqlBootstrapCommandRunner(final CommandRunner commandRunner, final PsqlBootstrapDiagnostics diagnostics) {
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    /**
     * Returns the run result.
     *
     * @param commandRequest command request value
     * @param connectionInfo connection info value
     * @param operation operation value
     * @param sqlSecret sql secret value
     * @return run result
     */
    public CommandResult run(
            final CommandRequest commandRequest,
            final PostgresConnectionInfo connectionInfo,
            final String operation,
            final Secret sqlSecret) {
        final CommandResult result = runCommand(commandRequest);
        requireSuccessful(operation, result, connectionInfo, sqlSecret);

        return result;
    }

    private CommandResult runCommand(final CommandRequest commandRequest) {
        try {
            return commandRunner.run(commandRequest);
        } catch (final ManagedPostgresException exception) {
            throw new PostgresStartupException(
                    "PostgreSQL bootstrap command failed",
                    exception,
                    diagnostics.commandRunnerFailure(Objects.toString(
                            exception.getMessage(),
                            exception.getClass().getName())));
        }
    }

    private void requireSuccessful(
            final String operation,
            final CommandResult result,
            final PostgresConnectionInfo connectionInfo,
            final Secret sqlSecret) {
        if (!result.successful()) {
            throw new PostgresStartupException(
                    "PostgreSQL bootstrap command failed",
                    diagnostics.commandFailure(operation, result, connectionInfo, sqlSecret));
        }
    }
}
