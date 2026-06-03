package eu.virtualparadox.managedpostgres.lifecycle.restore;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.diagnostics.CommandRedactor;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds redacted diagnostics for logical restore failures.
 */
public final class PostgresRestoreDiagnostics {

    /**
     * Creates a PostgresRestoreDiagnostics instance.
     */
    public PostgresRestoreDiagnostics() {}

    /**
     * Returns the invalid manifest result.
     *
     * @param reason reason value
     * @return invalid manifest result
     */
    public DiagnosticReport invalidManifest(final String reason) {
        return diagnostic("restore-manifest", Map.of("reason", reason));
    }

    /**
     * Returns the missing artifact result.
     *
     * @param kind kind value
     * @param path path value
     * @return missing artifact result
     */
    public DiagnosticReport missingArtifact(final String kind, final Path path) {
        return diagnostic(
                "restore-artifact",
                Map.of(
                        "kind",
                        kind,
                        "path",
                        Objects.requireNonNull(path, "path").toString()));
    }

    /**
     * Returns the incompatible artifact result.
     *
     * @param reason reason value
     * @return incompatible artifact result
     */
    public DiagnosticReport incompatibleArtifact(final String reason) {
        return diagnostic("restore-artifact", Map.of("reason", reason));
    }

    /**
     * Returns the safety backup required result.
     *
     * @return safety backup required result
     */
    public DiagnosticReport safetyBackupRequired() {
        return diagnostic("restore-safety-backup", Map.of("reason", "safety backup is required"));
    }

    /**
     * Returns the existing safety backup artifact result.
     *
     * @param path path value
     * @return existing safety backup artifact result
     */
    public DiagnosticReport existingSafetyBackupArtifact(final Path path) {
        return diagnostic(
                "restore-safety-backup",
                Map.of(
                        "reason",
                        "safety backup artifact already exists",
                        "path",
                        Objects.requireNonNull(path, "path").toString()));
    }

    /**
     * Returns the safety backup failure result.
     *
     * @param reason reason value
     * @param connectionInfo connection info value
     * @return safety backup failure result
     */
    public DiagnosticReport safetyBackupFailure(final String reason, final PostgresConnectionInfo connectionInfo) {
        return diagnostic("restore-safety-backup", Map.of("reason", redact(reason, connectionInfo)));
    }

    /**
     * Returns the command runner failure result.
     *
     * @param message message value
     * @param connectionInfo connection info value
     * @return command runner failure result
     */
    public DiagnosticReport commandRunnerFailure(final String message, final PostgresConnectionInfo connectionInfo) {
        return diagnostic("pg-restore", Map.of("message", redact(message, connectionInfo)));
    }

    /**
     * Returns the command failure result.
     *
     * @param result result value
     * @param connectionInfo connection info value
     * @return command failure result
     */
    public DiagnosticReport commandFailure(final CommandResult result, final PostgresConnectionInfo connectionInfo) {
        return diagnostic(
                "pg-restore",
                Map.of(
                        "command", redact(result.renderedCommand(), connectionInfo),
                        "exitCode", Integer.toString(result.exitCode()),
                        "stdout", redact(result.stdout(), connectionInfo),
                        "stderr", redact(result.stderr(), connectionInfo)));
    }

    /**
     * Returns the lock failure result.
     *
     * @param message message value
     * @param path path value
     * @return lock failure result
     */
    public DiagnosticReport lockFailure(final String message, final Path path) {
        return diagnostic(
                "restore-lock",
                Map.of(
                        "message",
                        message,
                        "path",
                        Objects.requireNonNull(path, "path").toString()));
    }

    private static DiagnosticReport diagnostic(final String sectionName, final Map<String, String> values) {
        return new DiagnosticReport(List.of(new DiagnosticSection(sectionName, values)));
    }

    private static String redact(final String value, final PostgresConnectionInfo connectionInfo) {
        final String commandRedacted = CommandRedactor.redact(Objects.requireNonNull(value, "value"));

        return commandRedacted.replace(connectionInfo.password().reveal(), "<redacted>");
    }
}
