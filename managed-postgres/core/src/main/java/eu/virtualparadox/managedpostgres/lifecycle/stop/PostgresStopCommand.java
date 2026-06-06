package eu.virtualparadox.managedpostgres.lifecycle.stop;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.exception.PostgresShutdownException;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.start.PgCtlController;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.runtime.RuntimeResolver;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Resolves a PostgreSQL runtime and executes the configured {@code pg_ctl stop}.
 */
public final class PostgresStopCommand {

    private final RuntimeResolver runtimeResolver;
    private final Duration shutdownTimeout;

    /**
     * Creates a PostgresStopCommand instance.
     *
     * @param runtimeResolver runtime resolver value
     * @param shutdownTimeout shutdown timeout value
     */
    public PostgresStopCommand(final RuntimeResolver runtimeResolver, final Duration shutdownTimeout) {
        this.runtimeResolver = Objects.requireNonNull(runtimeResolver, "runtimeResolver");
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
    }

    /**
     * Performs the stop operation.
     *
     * @param configuration configuration value
     * @param layout layout value
     */
    public void stop(final StartPostgresWorkflow.Configuration configuration, final PostgresLayout layout) {
        final Path runtimeDirectory = resolveRuntime(configuration);
        final CommandResult result;
        try {
            result = new PgCtlController(new CommandRunner(), runtimeDirectory)
                    .stop(layout.dataDirectory(), shutdownTimeout);
        } catch (final ManagedPostgresException exception) {
            throw new PostgresShutdownException(
                    "PostgreSQL pg_ctl stop command failed",
                    exception,
                    PostgresStopDiagnostics.wrappedFailure(
                            Objects.toString(
                                    exception.getMessage(), exception.getClass().getName()),
                            exception.diagnosticReport()));
        }
        if (!result.successful()) {
            throw new PostgresShutdownException(
                    "PostgreSQL pg_ctl stop failed", PostgresStopDiagnostics.commandFailure(result));
        }
    }

    private Path resolveRuntime(final StartPostgresWorkflow.Configuration configuration) {
        try {
            return runtimeResolver.resolve(configuration.runtimeSource(), configuration.postgresqlVersion());
        } catch (final ManagedPostgresException exception) {
            throw new PostgresShutdownException(
                    "PostgreSQL runtime resolution failed before stop",
                    exception,
                    PostgresStopDiagnostics.wrappedFailure(
                            Objects.toString(
                                    exception.getMessage(), exception.getClass().getName()),
                            exception.diagnosticReport()));
        }
    }
}
