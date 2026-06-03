package eu.virtualparadox.managedpostgres.lifecycle.start;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.exception.PostgresShutdownException;
import eu.virtualparadox.managedpostgres.lifecycle.PostgresStartupDiagnostics;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Stops a PostgreSQL instance started by this JVM.
 */
public final class StartedPostgresStopper {

    private final CommandRunner commandRunner;
    private final Path runtimeDirectory;
    private final Duration shutdownTimeout;

    /**
     * Creates a StartedPostgresStopper instance.
     *
     * @param runtimeDirectory runtime directory value
     * @param commandRunner command runner value
     * @param shutdownTimeout shutdown timeout value
     */
    public StartedPostgresStopper(
            final Path runtimeDirectory, final CommandRunner commandRunner, final Duration shutdownTimeout) {
        this.runtimeDirectory = Objects.requireNonNull(runtimeDirectory, "runtimeDirectory");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
    }

    /**
     * Performs the stop operation.
     *
     * @param layout layout value
     */
    public void stop(final PostgresLayout layout) {
        final CommandResult result;
        try {
            result = new PgCtlController(commandRunner, runtimeDirectory)
                    .stop(Objects.requireNonNull(layout, "layout").dataDirectory(), shutdownTimeout);
        } catch (final ManagedPostgresException exception) {
            throw new PostgresShutdownException(
                    "PostgreSQL pg_ctl stop command failed", exception, exception.diagnosticReport());
        }
        if (!result.successful()) {
            throw new PostgresShutdownException(
                    "PostgreSQL pg_ctl stop failed",
                    PostgresStartupDiagnostics.commandDiagnostic("pg_ctl-stop", result));
        }
    }
}
