package eu.virtualparadox.managedpostgres.lifecycle.backup;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.diagnostics.CommandRedactor;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;

/**
 * Builds redacted diagnostics for logical backup failures.
 */
public final class PostgresBackupDiagnostics {

    /**
     * Creates a PostgresBackupDiagnostics instance.
     */
    public PostgresBackupDiagnostics() {
    }

    /**
     * Returns the invalid target result.
     *
     * @param target target value
     * @return invalid target result
     */
    public DiagnosticReport invalidTarget(final Path target) {
        return diagnostic("postgres-backup-target", Map.of("target", Objects.toString(target)));
    }

    /**
     * Returns the existing artifact result.
     *
     * @param path path value
     * @return existing artifact result
     */
    public DiagnosticReport existingArtifact(final Path path) {
        return diagnostic("postgres-backup-artifact", Map.of("path", path.toString()));
    }

    /**
     * Returns the command runner failure result.
     *
     * @param message message value
     * @param connectionInfo connection info value
     * @return command runner failure result
     */
    public DiagnosticReport commandRunnerFailure(final String message, final PostgresConnectionInfo connectionInfo) {
        return diagnostic("pg-dump", Map.of("message", redact(message, connectionInfo)));
    }

    /**
     * Returns the command failure result.
     *
     * @param result result value
     * @param connectionInfo connection info value
     * @return command failure result
     */
    public DiagnosticReport commandFailure(final CommandResult result, final PostgresConnectionInfo connectionInfo) {
        return diagnostic("pg-dump", Map.of(
                "command", redact(result.renderedCommand(), connectionInfo),
                "exitCode", Integer.toString(result.exitCode()),
                "stdout", redact(result.stdout(), connectionInfo),
                "stderr", redact(result.stderr(), connectionInfo)));
    }

    /**
     * Returns the artifact write failure result.
     *
     * @param operation operation value
     * @param path path value
     * @return artifact write failure result
     */
    public DiagnosticReport artifactWriteFailure(final String operation, final Path path) {
        return diagnostic(operation, Map.of("path", path.toString()));
    }

    private static DiagnosticReport diagnostic(final String sectionName, final Map<String, String> values) {
        return new DiagnosticReport(List.of(new DiagnosticSection(sectionName, values)));
    }

    private static String redact(final String value, final PostgresConnectionInfo connectionInfo) {
        final String commandRedacted = CommandRedactor.redact(value);

        return commandRedacted.replace(connectionInfo.password().reveal(), "<redacted>");
    }
}
