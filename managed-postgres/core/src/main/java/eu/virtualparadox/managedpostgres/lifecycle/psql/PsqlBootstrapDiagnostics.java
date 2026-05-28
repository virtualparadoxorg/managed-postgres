package eu.virtualparadox.managedpostgres.lifecycle.psql;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.diagnostics.CommandRedactor;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.util.List;
import java.util.Map;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;

/**
 * Coordinates psql bootstrap diagnostics behavior for managed PostgreSQL internals.
 */
public final class PsqlBootstrapDiagnostics {

    /**
     * Creates a PsqlBootstrapDiagnostics instance.
     */
    public PsqlBootstrapDiagnostics() {
    }

    /**
     * Returns the command runner failure result.
     *
     * @param message message value
     * @return command runner failure result
     */
    public DiagnosticReport commandRunnerFailure(final String message) {
        return diagnostic("bootstrap-command", Map.of("message", message));
    }

    /**
     * Returns the command failure result.
     *
     * @param operation operation value
     * @param result result value
     * @param connectionInfo connection info value
     * @param sqlSecret sql secret value
     * @return command failure result
     */
    public DiagnosticReport commandFailure(
            final String operation,
            final CommandResult result,
            final PostgresConnectionInfo connectionInfo,
            final Secret sqlSecret) {
        return diagnostic(operation, Map.of(
                "command", redact(result.renderedCommand(), connectionInfo, sqlSecret),
                "exitCode", Integer.toString(result.exitCode()),
                "stdout", redact(result.stdout(), connectionInfo, sqlSecret),
                "stderr", redact(result.stderr(), connectionInfo, sqlSecret)));
    }

    /**
     * Returns the sql file failure result.
     *
     * @param sqlFile sql file value
     * @return sql file failure result
     */
    public DiagnosticReport sqlFileFailure(final String sqlFile) {
        return diagnostic("write-bootstrap-sql", Map.of("path", sqlFile));
    }

    private static DiagnosticReport diagnostic(final String sectionName, final Map<String, String> values) {
        return new DiagnosticReport(List.of(new DiagnosticSection(sectionName, values)));
    }

    private static String redact(
            final String value,
            final PostgresConnectionInfo connectionInfo,
            final Secret sqlSecret) {
        final String commandRedacted = CommandRedactor.redact(value);
        final String adminPasswordRedacted = commandRedacted.replace(connectionInfo.password().reveal(), "<redacted>");

        return adminPasswordRedacted.replace(sqlSecret.reveal(), "<redacted>");
    }
}
