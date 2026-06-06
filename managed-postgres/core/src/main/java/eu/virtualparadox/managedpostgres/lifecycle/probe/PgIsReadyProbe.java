package eu.virtualparadox.managedpostgres.lifecycle.probe;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRequest;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.runtime.RuntimeBinaryLocator;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs PostgreSQL's {@code pg_isready} readiness probe.
 */
public final class PgIsReadyProbe {

    private final Path pgIsReady;
    private final CommandRunner commandRunner;

    /**
     * Creates a readiness probe backed by a PostgreSQL runtime directory.
     *
     * @param runtimeDirectory PostgreSQL runtime directory
     * @param commandRunner external command runner
     */
    public PgIsReadyProbe(final Path runtimeDirectory, final CommandRunner commandRunner) {
        final Path checkedRuntimeDirectory = Objects.requireNonNull(runtimeDirectory, "runtimeDirectory");
        this.pgIsReady = pgIsReadyExecutable(checkedRuntimeDirectory);
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
    }

    /**
     * Probes connection readiness.
     *
     * @param connectionInfo connection details
     * @param timeout maximum command runtime
     * @return readiness probe result
     */
    public PostgresProbeResult probe(final PostgresConnectionInfo connectionInfo, final Duration timeout) {
        final CommandRequest request = CommandRequest.of(command(connectionInfo), timeout);
        PostgresProbeResult result;
        try {
            result = mapResult(commandRunner.run(request));
        } catch (ManagedPostgresException exception) {
            result = PostgresProbeResult.unhealthy("pg_isready could not run", exception.diagnosticReport());
        }

        return result;
    }

    private List<String> command(final PostgresConnectionInfo connectionInfo) {
        final PostgresConnectionInfo checkedConnectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        return List.of(
                pgIsReady.toString(),
                "-h",
                checkedConnectionInfo.host(),
                "-p",
                Integer.toString(checkedConnectionInfo.port()),
                "-d",
                checkedConnectionInfo.database(),
                "-U",
                checkedConnectionInfo.username());
    }

    private static PostgresProbeResult mapResult(final CommandResult commandResult) {
        final PostgresProbeResult result;
        if (commandResult.successful()) {
            result = PostgresProbeResult.healthy("pg_isready reports PostgreSQL healthy");
        } else {
            result = PostgresProbeResult.unhealthy(
                    "pg_isready reports PostgreSQL not ready", commandDiagnostic(commandResult));
        }

        return result;
    }

    private static DiagnosticReport commandDiagnostic(final CommandResult commandResult) {
        final DiagnosticSection section = new DiagnosticSection(
                "pg_isready",
                Map.of(
                        "command", commandResult.renderedCommand(),
                        "exitCode", Integer.toString(commandResult.exitCode()),
                        "stdout", commandResult.stdout(),
                        "stderr", commandResult.stderr()));

        return new DiagnosticReport(List.of(section));
    }

    private static Path pgIsReadyExecutable(final Path runtimeDirectory) {
        return RuntimeBinaryLocator.resolveBinary(runtimeDirectory, "pg_isready");
    }
}
