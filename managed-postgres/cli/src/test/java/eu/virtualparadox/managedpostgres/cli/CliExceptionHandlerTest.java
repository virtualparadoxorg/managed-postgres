package eu.virtualparadox.managedpostgres.cli;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.exception.PostgresAttachException;
import eu.virtualparadox.managedpostgres.exception.PostgresBackupException;
import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import eu.virtualparadox.managedpostgres.exception.PostgresShutdownException;
import eu.virtualparadox.managedpostgres.exception.PostgresStartupException;
import eu.virtualparadox.managedpostgres.exception.PostgresUpgradeException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CliExceptionHandlerTest {

    CliExceptionHandlerTest() {}

    @Test
    void managedPostgresExceptionMapsToSpecificExitCodeAndRendersDiagnostics() {
        final CliFailure failure =
                handle(new PostgresBackupException("backup failed", report("backup", Map.of("target", "backup.dump"))));

        assertThat(failure.exitCode()).isEqualTo(CliExitCode.BACKUP_RESTORE_ERROR.code());
        assertThat(failure.errorOutput()).contains("backup failed");
        assertThat(failure.errorOutput()).contains("backup");
        assertThat(failure.errorOutput()).contains("target=backup.dump");
    }

    @Test
    void restoreFailureMapsToBackupRestoreExitCode() {
        final CliFailure failure = handle(
                new PostgresRestoreException("restore failed", report("restore", Map.of("target", "backup.dump"))));

        assertThat(failure.exitCode()).isEqualTo(CliExitCode.BACKUP_RESTORE_ERROR.code());
        assertThat(failure.errorOutput()).contains("restore failed");
    }

    @Test
    void startupTimeoutMapsToReadinessTimeoutExitCode() {
        final CliFailure failure = handle(new PostgresStartupException(
                "postgres did not become ready", report("startup-timeout", Map.of("timeout", "PT60S"))));

        assertThat(failure.exitCode()).isEqualTo(CliExitCode.READINESS_TIMEOUT.code());
        assertThat(failure.errorOutput()).contains("startup-timeout");
    }

    @Test
    void startupFailureWithoutTimeoutMapsToStartupExitCode() {
        final CliFailure failure = handle(new PostgresStartupException(
                "postgres exited before ready", report("startup", Map.of("exitCode", "1"))));

        assertThat(failure.exitCode()).isEqualTo(CliExitCode.STARTUP_ERROR.code());
        assertThat(failure.errorOutput()).contains("startup");
    }

    @Test
    void upgradeFailureMapsToVersionMismatchExitCode() {
        final CliFailure failure = handle(new PostgresUpgradeException(
                "major version mismatch", report("postgres-version", Map.of("requested", "17.0", "existing", "16"))));

        assertThat(failure.exitCode()).isEqualTo(CliExitCode.VERSION_MISMATCH.code());
        assertThat(failure.errorOutput()).contains("major version mismatch");
    }

    @Test
    void lockDiagnosticMapsToLockUnavailableExitCode() {
        final CliFailure failure = handle(new ManagedPostgresException(
                "lock unavailable", report("postgres-lock", Map.of("path", ".local/postgres/locks/app-db.lock"))));

        assertThat(failure.exitCode()).isEqualTo(CliExitCode.LOCK_UNAVAILABLE.code());
        assertThat(failure.errorOutput()).contains("postgres-lock");
    }

    @Test
    void runtimeDiagnosticMapsToRuntimeExitCode() {
        final CliFailure failure = handle(new ManagedPostgresException(
                "runtime missing", report("runtime-resolution", Map.of("path", "postgres/bin"))));

        assertThat(failure.exitCode()).isEqualTo(CliExitCode.RUNTIME_ERROR.code());
        assertThat(failure.errorOutput()).contains("runtime-resolution");
    }

    @Test
    void clusterDiagnosticMapsToClusterExitCode() {
        final CliFailure failure = handle(
                new ManagedPostgresException("metadata corrupt", report("metadata", Map.of("path", "metadata.json"))));

        assertThat(failure.exitCode()).isEqualTo(CliExitCode.CLUSTER_ERROR.code());
        assertThat(failure.errorOutput()).contains("metadata");
    }

    @Test
    void unclassifiedManagedFailureMapsToGenericExitCode() {
        final CliFailure failure = handle(new ManagedPostgresException(
                "unknown managed failure", report("unclassified", Map.of("detail", "unknown"))));

        assertThat(failure.exitCode()).isEqualTo(CliExitCode.GENERIC_ERROR.code());
        assertThat(failure.errorOutput()).contains("unclassified");
    }

    @Test
    void shutdownFailureMapsToClusterExitCode() {
        final CliFailure failure =
                handle(new PostgresShutdownException("shutdown failed", report("shutdown", Map.of("mode", "fast"))));

        assertThat(failure.exitCode()).isEqualTo(CliExitCode.CLUSTER_ERROR.code());
        assertThat(failure.errorOutput()).contains("shutdown failed");
    }

    @Test
    void attachFailureMapsToClusterExitCode() {
        final CliFailure failure = handle(
                new PostgresAttachException("attach failed", report("attach", Map.of("reason", "not compatible"))));

        assertThat(failure.exitCode()).isEqualTo(CliExitCode.CLUSTER_ERROR.code());
        assertThat(failure.errorOutput()).contains("attach failed");
    }

    @Test
    void illegalArgumentMapsToConfigurationErrorAndRendersUsageMessage() {
        final CliFailure failure = handle(new IllegalArgumentException("name must not be blank"));

        assertThat(failure.exitCode()).isEqualTo(CliExitCode.CONFIGURATION_ERROR.code());
        assertThat(failure.errorOutput()).contains("Configuration error: name must not be blank");
    }

    @Test
    void runtimeExceptionMapsToGenericErrorAndDoesNotPrintStackTraceByDefault() {
        final CliFailure failure = handle(new RuntimeException("unexpected failure"));

        assertThat(failure.exitCode()).isEqualTo(CliExitCode.GENERIC_ERROR.code());
        assertThat(failure.errorOutput()).contains("Unexpected error: unexpected failure");
        assertThat(failure.errorOutput()).doesNotContain("at eu.virtualparadox");
    }

    @Test
    void secretLookingValuesAreRedactedFromRenderedFailures() {
        final CliFailure failure = handle(new ManagedPostgresException(
                "command failed password=plain-secret",
                report(
                        "command",
                        Map.of(
                                "password", "super-secret",
                                "environment", "PGPASSWORD=another-secret"))));

        assertThat(failure.errorOutput())
                .contains("password=<redacted>")
                .contains("PGPASSWORD=<redacted>")
                .doesNotContain("plain-secret")
                .doesNotContain("super-secret")
                .doesNotContain("another-secret");
    }

    private static CliFailure handle(final Throwable throwable) {
        final ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        final CliFailure failure;

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(errorOutput, StandardCharsets.UTF_8), true)) {
            final CliExceptionHandler handler = new CliExceptionHandler(writer);
            final int exitCode = handler.handle(throwable);
            writer.flush();
            failure = new CliFailure(exitCode, errorOutput.toString(StandardCharsets.UTF_8));
        }

        return failure;
    }

    private static DiagnosticReport report(final String name, final Map<String, String> values) {
        return new DiagnosticReport(java.util.List.of(new DiagnosticSection(name, values)));
    }

    private record CliFailure(int exitCode, String errorOutput) {}
}
