package eu.virtualparadox.managedpostgres.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioManagedPostgres;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioShell;
import eu.virtualparadox.managedpostgres.test.FakePostgresRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("driver-manager")
final class FakeRuntimeRestoreIT {

    @TempDir
    private Path temporaryDirectory;

    FakeRuntimeRestoreIT() {}

    @Test
    void localStartBackupThenRestoreCreatesSafetyBackupAndRunsPgRestore() throws IOException {
        final Path commandLog = temporaryDirectory.resolve("postgres-command-calls.log");
        final FakePostgresRuntime runtime = runtime(commandLog);
        final Path storageRoot = temporaryDirectory.resolve("cluster");
        final Path backup = temporaryDirectory.resolve("backups").resolve("app.dump");

        try (RunningPostgres postgres =
                ScenarioManagedPostgres.applicationCluster(storageRoot, runtime).start()) {
            postgres.backupTo(backup);
            postgres.restoreFrom(
                    backup,
                    RestoreOptions.builder()
                            .dropCurrentDatabase(true)
                            .createSafetyBackup(true)
                            .build());
        }

        final Path safetyBackup = temporaryDirectory.resolve("backups").resolve("app.before-restore.dump");
        assertThat(safetyBackup).hasContent("fake dump\n");
        assertThat(manifestPath(safetyBackup)).isRegularFile();
        assertThat(checksumPath(safetyBackup)).isRegularFile();
        assertThat(Files.readAllLines(commandLog))
                .filteredOn(call -> call.startsWith("pg_restore "))
                .singleElement()
                .satisfies(call -> assertThat(call)
                        .contains("-d app")
                        .contains("-U app_owner")
                        .contains("--clean")
                        .contains("--if-exists")
                        .contains("--no-owner")
                        .contains(backup.toString()));
        assertThat(Files.readString(commandLog))
                .contains("PGPASSWORD=set")
                .doesNotContain("app-password")
                .doesNotContain("test-password");
    }

    private FakePostgresRuntime runtime(final Path commandLog) throws IOException {
        return FakePostgresRuntime.create(
                temporaryDirectory.resolve("runtime"),
                ScenarioShell.recordingBootstrapPsql(temporaryDirectory.resolve("psql-calls.log")),
                ScenarioShell.recordingPgDump(commandLog),
                ScenarioShell.recordingPgRestore(commandLog));
    }

    private static Path manifestPath(final Path target) {
        return Path.of(target + ".manifest.json");
    }

    private static Path checksumPath(final Path target) {
        return Path.of(target + ".sha256");
    }
}
