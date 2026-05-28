package eu.virtualparadox.managedpostgres.lifecycle;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.exception.PostgresStartupException;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.probe.PostgresProbeResult;

/**
 * Builds startup diagnostics for PostgreSQL lifecycle failures.
 */
public final class PostgresStartupDiagnostics {

    private PostgresStartupDiagnostics() {
    }

    /**
     * Returns the diagnostic result.
     *
     * @param sectionName section name value
     * @param values values value
     * @return diagnostic result
     */
    public static DiagnosticReport diagnostic(final String sectionName, final Map<String, String> values) {
        return new DiagnosticReport(List.of(new DiagnosticSection(sectionName, values)));
    }

    /**
     * Returns the command diagnostic result.
     *
     * @param sectionName section name value
     * @param commandResult command result value
     * @return command diagnostic result
     */
    public static DiagnosticReport commandDiagnostic(final String sectionName, final CommandResult commandResult) {
        return diagnostic(sectionName, Map.of(
                "command", commandResult.renderedCommand(),
                "exitCode", Integer.toString(commandResult.exitCode()),
                "stdout", commandResult.stdout(),
                "stderr", commandResult.stderr()));
    }

    /**
     * Returns the command failure result.
     *
     * @param message message value
     * @param sectionName section name value
     * @param exception exception value
     * @return command failure result
     */
    public static PostgresStartupException commandFailure(
            final String message,
            final String sectionName,
            final ManagedPostgresException exception) {
        final List<DiagnosticSection> sections = new ArrayList<>();
        sections.add(new DiagnosticSection(
                sectionName,
                Map.of("message", exceptionMessage(exception))));
        sections.addAll(exception.diagnosticReport().sections());

        return new PostgresStartupException(message, exception, new DiagnosticReport(sections));
    }

    /**
     * Returns the startup failure result.
     *
     * @param message message value
     * @param exception exception value
     * @param sectionName section name value
     * @param values values value
     * @return startup failure result
     */
    public static PostgresStartupException startupFailure(
            final String message,
            final RuntimeException exception,
            final String sectionName,
            final Map<String, String> values) {
        final PostgresStartupException failure;
        if (exception instanceof PostgresStartupException startupException) {
            failure = startupException;
        } else {
            failure = new PostgresStartupException(message, exception, diagnostic(sectionName, values));
        }

        return failure;
    }

    /**
     * Returns the startup timeout result.
     *
     * @param connectionInfo connection info value
     * @param layout layout value
     * @param lastProbeResult last probe result value
     * @return startup timeout result
     */
    public static PostgresStartupException startupTimeout(
            final PostgresConnectionInfo connectionInfo,
            final PostgresLayout layout,
            final PostgresProbeResult lastProbeResult) {
        final List<DiagnosticSection> sections = new ArrayList<>();
        sections.add(new DiagnosticSection(
                "startup-timeout",
                Map.of(
                        "host", connectionInfo.host(),
                        "port", Integer.toString(connectionInfo.port()),
                        "dataDirectory", layout.dataDirectory().toString(),
                        "metadataPath", layout.metadataPath().toString(),
                        "lastProbe", lastProbeResult.summary())));
        sections.addAll(lastProbeResult.diagnosticReport().sections());

        return new PostgresStartupException(
                "Timed out waiting for PostgreSQL readiness",
                new DiagnosticReport(sections));
    }

    private static String exceptionMessage(final RuntimeException exception) {
        return Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getSimpleName());
    }
}
