package eu.virtualparadox.managedpostgres.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioManagedPostgres;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioMetadata;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioShell;
import eu.virtualparadox.managedpostgres.test.FakePostgresRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FakeRuntimeConfiguredStopIT {

    @TempDir
    private Path temporaryDirectory;

    FakeRuntimeConfiguredStopIT() {}

    @Test
    void configuredStopStopsMetadataBackedPersistentRuntime() throws IOException {
        final Path callLog = temporaryDirectory.resolve("pg_ctl-calls.log");
        final FakePostgresRuntime runtime = FakePostgresRuntime.create(
                temporaryDirectory.resolve("runtime"), ScenarioShell.recordingPgCtl(callLog));
        final Path storageRoot = temporaryDirectory.resolve("cluster");

        try (RunningPostgres running = ScenarioManagedPostgres.applicationCluster(storageRoot, runtime)
                .stopPolicy(StopPolicy.KEEP_RUNNING)
                .start()) {
            assertThat(running.status()).isEqualTo(PostgresStatus.RUNNING);
            assertThat(ScenarioMetadata.metadataPath(storageRoot)).isRegularFile();
        }
        final int selectedPort = ScenarioMetadata.require(storageRoot).port();

        assertThat(Files.readAllLines(callLog)).containsExactly("start");
        assertThat(ScenarioMetadata.metadataPath(storageRoot)).isRegularFile();

        try (ManagedPostgres postgres =
                ScenarioManagedPostgres.applicationCluster(storageRoot, runtime).build()) {
            postgres.stop();

            assertThat(postgres.status()).isEqualTo(PostgresStatus.STOPPED);
        }

        assertThat(Files.readAllLines(callLog)).containsExactly("start", "stop");
        assertThat(ScenarioMetadata.read(storageRoot)).isEmpty();
        assertThat(ScenarioMetadata.readPort(storageRoot)).hasValue(selectedPort);
    }
}
