package eu.virtualparadox.managedpostgres.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import eu.virtualparadox.managedpostgres.cli.command.support.CliCommandTestSupport;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgres;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgresFactory;
import eu.virtualparadox.managedpostgres.cli.command.support.TestRunningPostgres;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.exception.PostgresBackupException;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class BackupCommandTest {

    private static final Path BACKUP = Path.of("target/backups/app.dump");

    BackupCommandTest() {}

    @Test
    void backupCommandAcceptsBackupPathAsFirstPositionalParameter() {
        final TestRunningPostgres runningPostgres = TestRunningPostgres.withConnection(connectionInfo());

        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withRunning(runningPostgres)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runBackup(postgres, BACKUP.toString());

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(runningPostgres.backupTarget()).hasValue(BACKUP);
        }
    }

    @Test
    void backupCommandStartsThroughConfiguredManagedPostgresAndBacksUp() {
        final TestRunningPostgres runningPostgres = TestRunningPostgres.withConnection(connectionInfo());

        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withRunning(runningPostgres)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runBackup(postgres, BACKUP.toString());

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(postgres.invocations().start()).isEqualTo(1);
            assertThat(runningPostgres.backupInvocations()).isEqualTo(1);
        }
    }

    @Test
    void missingBackupPathIsUsageError() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withConnection(connectionInfo())) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runBackup(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.CONFIGURATION_ERROR.code());
            assertThat(run.errorOutput()).contains("Missing required");
        }
    }

    @Test
    void backupExceptionMapsToBackupRestoreExitCode() {
        final DiagnosticReport diagnostics =
                new DiagnosticReport(List.of(new DiagnosticSection("backup", Map.of("password", "secret-password"))));
        final PostgresBackupException failure =
                new PostgresBackupException("backup failed password=secret-password", diagnostics);
        final TestRunningPostgres runningPostgres = TestRunningPostgres.withBackupFailure(connectionInfo(), failure);

        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withRunning(runningPostgres)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runBackup(postgres, BACKUP.toString());

            assertThat(run.exitCode()).isEqualTo(CliExitCode.BACKUP_RESTORE_ERROR.code());
            assertThat(run.errorOutput()).contains("Managed Postgres error").doesNotContain("secret-password");
        }
    }

    @Test
    void backupOutputNeverIncludesPassword() {
        final TestRunningPostgres runningPostgres = TestRunningPostgres.withConnection(connectionInfo());

        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withRunning(runningPostgres)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runBackup(postgres, BACKUP.toString());

            assertThat(run.output()).doesNotContain("secret-password");
            assertThat(run.errorOutput()).doesNotContain("secret-password");
        }
    }

    private static PostgresConnectionInfo connectionInfo() {
        return new PostgresConnectionInfo("127.0.0.1", 15432, "app", "app", Secret.of("secret-password"));
    }
}
