package eu.virtualparadox.managedpostgres.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import eu.virtualparadox.managedpostgres.cli.command.support.CliCommandTestSupport;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgres;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgresFactory;
import eu.virtualparadox.managedpostgres.cli.command.support.TestRunningPostgres;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class RestoreCommandTest {

    private static final Path BACKUP = Path.of("target/backups/app.dump");

    RestoreCommandTest() {
    }

    @Test
    void restoreCommandAcceptsBackupPathAsFirstPositionalParameter() {
        final TestRunningPostgres runningPostgres = TestRunningPostgres.withConnection(connectionInfo());

        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withRunning(runningPostgres)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runRestore(
                    postgres,
                    "--drop-current-database",
                    "--create-safety-backup",
                    BACKUP.toString());

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(runningPostgres.restoreBackup()).hasValue(BACKUP);
        }
    }

    @Test
    void restoreCommandRequiresDropCurrentDatabase() {
        final TestRunningPostgres runningPostgres = TestRunningPostgres.withConnection(connectionInfo());

        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withRunning(runningPostgres)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runRestore(
                    postgres,
                    "--create-safety-backup",
                    BACKUP.toString());

            assertThat(run.exitCode()).isEqualTo(CliExitCode.CONFIGURATION_ERROR.code());
            assertThat(run.errorOutput()).contains("--drop-current-database");
            assertThat(runningPostgres.restoreInvocations()).isZero();
        }
    }

    @Test
    void restoreCommandRequiresCreateSafetyBackup() {
        final TestRunningPostgres runningPostgres = TestRunningPostgres.withConnection(connectionInfo());

        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withRunning(runningPostgres)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runRestore(
                    postgres,
                    "--drop-current-database",
                    BACKUP.toString());

            assertThat(run.exitCode()).isEqualTo(CliExitCode.CONFIGURATION_ERROR.code());
            assertThat(run.errorOutput()).contains("--create-safety-backup");
            assertThat(runningPostgres.restoreInvocations()).isZero();
        }
    }

    @Test
    void restoreCommandCallsRestoreFromWithExplicitSafetyOptions() {
        final TestRunningPostgres runningPostgres = TestRunningPostgres.withConnection(connectionInfo());

        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withRunning(runningPostgres)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runRestore(
                    postgres,
                    "--drop-current-database",
                    "--create-safety-backup",
                    BACKUP.toString());

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(runningPostgres.restoreOptions()).hasValueSatisfying(RestoreCommandTest::assertSafetyOptions);
        }
    }

    @Test
    void restoreExceptionMapsToBackupRestoreExitCode() {
        final DiagnosticReport diagnostics = new DiagnosticReport(List.of(new DiagnosticSection(
                "restore",
                Map.of("password", "secret-password"))));
        final PostgresRestoreException failure = new PostgresRestoreException(
                "restore failed password=secret-password",
                diagnostics);
        final TestRunningPostgres runningPostgres = TestRunningPostgres.withRestoreFailure(
                connectionInfo(),
                failure);

        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withRunning(runningPostgres)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runRestore(
                    postgres,
                    "--drop-current-database",
                    "--create-safety-backup",
                    BACKUP.toString());

            assertThat(run.exitCode()).isEqualTo(CliExitCode.BACKUP_RESTORE_ERROR.code());
            assertThat(run.errorOutput())
                    .contains("Managed Postgres error")
                    .doesNotContain("secret-password");
        }
    }

    @Test
    void restoreOutputNeverIncludesPassword() {
        final TestRunningPostgres runningPostgres = TestRunningPostgres.withConnection(connectionInfo());

        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withRunning(runningPostgres)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runRestore(
                    postgres,
                    "--drop-current-database",
                    "--create-safety-backup",
                    BACKUP.toString());

            assertThat(run.output()).doesNotContain("secret-password");
            assertThat(run.errorOutput()).doesNotContain("secret-password");
        }
    }

    private static void assertSafetyOptions(final RestoreOptions options) {
        assertThat(options.dropCurrentDatabase()).isTrue();
        assertThat(options.createSafetyBackup()).isTrue();
    }

    private static PostgresConnectionInfo connectionInfo() {
        return new PostgresConnectionInfo(
                "127.0.0.1",
                15432,
                "app",
                "app",
                Secret.of("secret-password"));
    }
}
