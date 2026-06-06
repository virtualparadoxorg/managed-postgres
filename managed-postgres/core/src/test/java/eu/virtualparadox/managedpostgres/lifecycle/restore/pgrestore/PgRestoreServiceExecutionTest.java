package eu.virtualparadox.managedpostgres.lifecycle.restore.pgrestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import eu.virtualparadox.managedpostgres.lifecycle.backup.BackupChecksum;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.restore.PgRestoreBackupFixture;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.restore.PgRestoreRuntimeFixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class PgRestoreServiceExecutionTest {

    @TempDir
    private Path temporaryDirectory;

    PgRestoreServiceExecutionTest() {}

    @Test
    void restoreCreatesSafetyBackupBeforeRunningPgRestore() throws IOException {
        final PgRestoreServiceFixture serviceFixture = new PgRestoreServiceFixture(temporaryDirectory);
        final PgRestoreRuntimeFixture.TestRuntime runtime =
                new PgRestoreRuntimeFixture(temporaryDirectory).createRuntime(0);
        final Path backup = new PgRestoreBackupFixture(temporaryDirectory).writeValidBackup("managed backup\n");

        serviceFixture.service(runtime.runtimeDirectory()).restoreFrom(backup, serviceFixture.destructiveOptions());

        final Path safetyBackup = temporaryDirectory.resolve("backups").resolve("app.before-restore.dump");
        assertThat(safetyBackup).hasContent("safety dump\n");
        assertThat(PgRestoreBackupFixture.manifestPath(safetyBackup)).isRegularFile();
        assertThat(PgRestoreBackupFixture.checksumPath(safetyBackup))
                .hasContent(BackupChecksum.sha256(safetyBackup) + "  app.before-restore.dump\n");
        final String commandLog = Files.readString(runtime.commandLog(), StandardCharsets.UTF_8);
        assertThat(commandLog.indexOf("PG_DUMP\n")).isLessThan(commandLog.indexOf("PG_RESTORE\n"));
        assertThat(commandLog)
                .contains("PG_RESTORE_EXEC="
                        + runtime.runtimeDirectory().resolve("bin").resolve("pg_restore"))
                .contains("PG_RESTORE_PGPASSWORD=set")
                .contains("-h 127.0.0.1")
                .contains("-p 55432")
                .contains("-U app")
                .contains("-d app")
                .contains("--clean")
                .contains("--if-exists")
                .contains("--no-owner")
                .contains(backup.toString())
                .doesNotContain("app-password");
    }

    @Test
    void failedPgRestoreLeavesOriginalAndSafetyBackupAndRedactsDiagnostics() throws IOException {
        final PgRestoreServiceFixture serviceFixture = new PgRestoreServiceFixture(temporaryDirectory);
        final PgRestoreRuntimeFixture.TestRuntime runtime =
                new PgRestoreRuntimeFixture(temporaryDirectory).createRuntime(7);
        final Path backup = new PgRestoreBackupFixture(temporaryDirectory).writeValidBackup("managed backup\n");

        assertThatThrownBy(() -> serviceFixture
                        .service(runtime.runtimeDirectory())
                        .restoreFrom(backup, serviceFixture.destructiveOptions()))
                .isInstanceOf(PostgresRestoreException.class)
                .satisfies(throwable -> assertThat(((PostgresRestoreException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("<redacted>")
                        .doesNotContain("app-password"));

        final Path safetyBackup = temporaryDirectory.resolve("backups").resolve("app.before-restore.dump");
        assertThat(backup).hasContent("managed backup\n");
        assertThat(safetyBackup).hasContent("safety dump\n");
        assertThat(PgRestoreBackupFixture.manifestPath(safetyBackup)).isRegularFile();
        assertThat(PgRestoreBackupFixture.checksumPath(safetyBackup)).isRegularFile();
    }
}
