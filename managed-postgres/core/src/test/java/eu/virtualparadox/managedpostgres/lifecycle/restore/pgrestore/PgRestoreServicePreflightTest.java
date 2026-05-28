package eu.virtualparadox.managedpostgres.lifecycle.restore.pgrestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import eu.virtualparadox.managedpostgres.lifecycle.layout.HeldPostgresLock;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.restore.PgRestoreBackupFixture;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.restore.PgRestoreRuntimeFixture;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;

public final class PgRestoreServicePreflightTest {

    @TempDir
    private Path temporaryDirectory;

    PgRestoreServicePreflightTest() {
    }

    @Test
    void restoreValidatesBackupArtifactsBeforeRunningCommands() throws IOException {
        final PgRestoreServiceFixture serviceFixture = new PgRestoreServiceFixture(temporaryDirectory);
        final PgRestoreRuntimeFixture.TestRuntime runtime = runtimeFixture().createRuntime(0);
        final Path backup = backupFixture().writeValidBackup("managed backup\n");
        Files.writeString(Path.of(backup + ".sha256"), "wrong  app.dump\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> serviceFixture.service(runtime.runtimeDirectory())
                .restoreFrom(backup, serviceFixture.destructiveOptions()))
                .isInstanceOf(PostgresRestoreException.class)
                .hasMessageContaining("checksum");

        assertThat(runtime.commandLog()).doesNotExist();
    }

    @Test
    void restoreRequiresExplicitSafetyBackupInFoundationWave() throws IOException {
        final PgRestoreServiceFixture serviceFixture = new PgRestoreServiceFixture(temporaryDirectory);
        final PgRestoreRuntimeFixture.TestRuntime runtime = runtimeFixture().createRuntime(0);
        final Path backup = backupFixture().writeValidBackup("managed backup\n");
        final RestoreOptions options = RestoreOptions.builder()
                .dropCurrentDatabase(true)
                .createSafetyBackup(false)
                .build();

        assertThatThrownBy(() -> serviceFixture.service(runtime.runtimeDirectory()).restoreFrom(backup, options))
                .isInstanceOf(PostgresRestoreException.class)
                .hasMessageContaining("safety backup");

        assertThat(runtime.commandLog()).doesNotExist();
    }

    @Test
    void existingSafetyBackupFailsBeforeRunningPgRestore() throws IOException {
        final PgRestoreServiceFixture serviceFixture = new PgRestoreServiceFixture(temporaryDirectory);
        final PgRestoreRuntimeFixture.TestRuntime runtime = runtimeFixture().createRuntime(0);
        final Path backup = backupFixture().writeValidBackup("managed backup\n");
        final Path safetyBackup = temporaryDirectory.resolve("backups").resolve("app.before-restore.dump");
        Files.writeString(safetyBackup, "existing", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> serviceFixture.service(runtime.runtimeDirectory())
                .restoreFrom(backup, serviceFixture.destructiveOptions()))
                .isInstanceOf(PostgresRestoreException.class)
                .hasMessageContaining("safety backup");

        assertThat(runtime.commandLog()).doesNotExist();
    }

    @Test
    void heldOperationLockPreventsRestoreCommands() throws IOException {
        final PgRestoreServiceFixture serviceFixture = new PgRestoreServiceFixture(temporaryDirectory);
        final PgRestoreRuntimeFixture.TestRuntime runtime = runtimeFixture().createRuntime(0);
        final Path backup = backupFixture().writeValidBackup("managed backup\n");
        final PostgresLayout layout = serviceFixture.layout();
        final PostgresLockService lockService = new PostgresLockService();
        final PgRestoreService service = serviceFixture.service(runtime.runtimeDirectory(), layout, lockService);

        try (HeldPostgresLock heldLock = lockService.acquireOperationLock(layout)) {
            assertThat(heldLock.path()).isEqualTo(layout.operationLockPath());
            assertThatThrownBy(() -> service.restoreFrom(backup, serviceFixture.destructiveOptions()))
                    .isInstanceOf(PostgresRestoreException.class)
                    .hasMessageContaining("lock");
        }

        assertThat(runtime.commandLog()).doesNotExist();
    }

    private PgRestoreRuntimeFixture runtimeFixture() {
        return new PgRestoreRuntimeFixture(temporaryDirectory);
    }

    private PgRestoreBackupFixture backupFixture() {
        return new PgRestoreBackupFixture(temporaryDirectory);
    }
}
