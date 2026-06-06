package eu.virtualparadox.managedpostgres.lifecycle.stop;

import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.lifecycle.PostgresStartupDiagnostics;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds diagnostics for configured PostgreSQL stop failures.
 */
public final class PostgresStopDiagnostics {

    private PostgresStopDiagnostics() {}

    /**
     * Returns the mismatch result.
     *
     * @param layout layout value
     * @param reason reason value
     * @return mismatch result
     */
    public static DiagnosticReport mismatch(final PostgresLayout layout, final String reason) {
        return diagnostic(Map.of(
                "root", Objects.requireNonNull(layout, "layout").root().toString(),
                "reason", Objects.requireNonNull(reason, "reason")));
    }

    /**
     * Returns the command failure result.
     *
     * @param commandResult command result value
     * @return command failure result
     */
    public static DiagnosticReport commandFailure(final CommandResult commandResult) {
        return PostgresStartupDiagnostics.commandDiagnostic(
                "postgres-stop", Objects.requireNonNull(commandResult, "commandResult"));
    }

    /**
     * Returns the wrapped failure result.
     *
     * @param message message value
     * @param diagnosticReport diagnostic report value
     * @return wrapped failure result
     */
    public static DiagnosticReport wrappedFailure(final String message, final DiagnosticReport diagnosticReport) {
        final List<DiagnosticSection> sections = new java.util.ArrayList<>();
        sections.add(
                new DiagnosticSection("postgres-stop", Map.of("message", Objects.requireNonNull(message, "message"))));
        sections.addAll(
                Objects.requireNonNull(diagnosticReport, "diagnosticReport").sections());

        return new DiagnosticReport(sections);
    }

    private static DiagnosticReport diagnostic(final Map<String, String> values) {
        return new DiagnosticReport(List.of(new DiagnosticSection("postgres-stop", values)));
    }
}
