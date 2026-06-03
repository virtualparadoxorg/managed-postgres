package eu.virtualparadox.managedpostgres.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.exception.PostgresBackupException;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.scenario.support.LoopbackTcpServer;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioJdbcDriver;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioManagedPostgres;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioMetadata;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioShell;
import eu.virtualparadox.managedpostgres.test.FakePostgresRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("driver-manager")
final class FakeRuntimeBackupIT {

    @TempDir
    private Path temporaryDirectory;

    FakeRuntimeBackupIT() {}

    @Test
    void localStartBackupCreatesDumpManifestAndChecksum() throws IOException {
        final Path pgDumpLog = temporaryDirectory.resolve("pg-dump-calls.log");
        final FakePostgresRuntime runtime = runtime(pgDumpLog);
        final Path storageRoot = temporaryDirectory.resolve("cluster");
        final Path target = temporaryDirectory.resolve("backups").resolve("app.dump");

        try (RunningPostgres postgres =
                ScenarioManagedPostgres.applicationCluster(storageRoot, runtime).start()) {
            postgres.backupTo(target);
        }

        assertThat(target).hasContent("fake dump\n");
        assertThat(Files.readString(manifestPath(target)))
                .contains("\"postgresqlVersion\": \"16.4\"")
                .contains("\"clusterId\":")
                .contains("\"database\": \"app\"")
                .contains("\"format\": \"pg_dump_custom\"");
        assertThat(Files.readString(checksumPath(target))).contains("  app.dump\n");
        assertThat(Files.readAllLines(pgDumpLog))
                .contains("PGPASSWORD=set")
                .anySatisfy(call -> assertThat(call)
                        .startsWith("pg_dump ")
                        .contains("-Fc")
                        .contains("-d app")
                        .contains("-U app_owner"))
                .allSatisfy(call -> assertThat(call).doesNotContain("app-password"));
    }

    @Test
    void attachedHandleCanCreateBackupAndExistingTargetFails() throws IOException, SQLException {
        final Path pgDumpLog = temporaryDirectory.resolve("pg-dump-calls.log");
        final FakePostgresRuntime runtime = runtime(pgDumpLog);
        final Path storageRoot = temporaryDirectory.resolve("cluster");
        final Path firstTarget = temporaryDirectory.resolve("backups").resolve("first.dump");
        final Path secondTarget = temporaryDirectory.resolve("backups").resolve("second.dump");

        try (RunningPostgres first =
                ScenarioManagedPostgres.applicationCluster(storageRoot, runtime).start()) {
            first.backupTo(firstTarget);
            final PostgresInstanceMetadata metadata = ScenarioMetadata.require(storageRoot);

            try (LoopbackTcpServer server = LoopbackTcpServer.open(metadata.host(), metadata.port());
                    ScenarioJdbcDriver driver = ScenarioJdbcDriver.register(metadata);
                    RunningPostgres second = ScenarioManagedPostgres.applicationCluster(storageRoot, runtime)
                            .reuseExisting()
                            .start()) {
                server.assertHealthy();
                second.backupTo(secondTarget);
                assertThat(driver.connections()).isPositive();
            }

            assertThatThrownBy(() -> first.backupTo(firstTarget))
                    .isInstanceOf(PostgresBackupException.class)
                    .hasMessageContaining("already exists");
        }

        assertThat(firstTarget).hasContent("fake dump\n");
        assertThat(secondTarget).hasContent("fake dump\n");
        assertThat(Files.readAllLines(pgDumpLog).stream().filter(call -> call.startsWith("pg_dump ")))
                .hasSize(2)
                .allSatisfy(call -> assertThat(call).doesNotContain("app-password"));
    }

    private FakePostgresRuntime runtime(final Path pgDumpLog) throws IOException {
        return FakePostgresRuntime.create(
                temporaryDirectory.resolve("runtime"),
                ScenarioShell.recordingBootstrapPsql(temporaryDirectory.resolve("psql-calls.log")),
                ScenarioShell.recordingPgDump(pgDumpLog));
    }

    private static Path manifestPath(final Path target) {
        return Path.of(target + ".manifest.json");
    }

    private static Path checksumPath(final Path target) {
        return Path.of(target + ".sha256");
    }
}
