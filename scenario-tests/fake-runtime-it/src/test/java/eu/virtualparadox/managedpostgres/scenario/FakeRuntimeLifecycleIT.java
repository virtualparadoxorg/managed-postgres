package eu.virtualparadox.managedpostgres.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.ManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioMetadata;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioShell;
import eu.virtualparadox.managedpostgres.security.Secret;
import eu.virtualparadox.managedpostgres.spi.ManagedPostgresConfigurer;
import eu.virtualparadox.managedpostgres.test.FakePostgresRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FakeRuntimeLifecycleIT {

    @TempDir
    private Path temporaryDirectory;

    FakeRuntimeLifecycleIT() {}

    @Test
    void temporaryStartReadyCloseStopsAndDeletesOwnedTempCluster() throws IOException {
        final Path callLog = temporaryDirectory.resolve("pg_ctl-calls.log");
        final FakePostgresRuntime runtime = FakePostgresRuntime.create(
                temporaryDirectory.resolve("runtime"), ScenarioShell.recordingPgCtl(callLog));
        final Path temporaryRoot = temporaryDirectory.resolve("temporary-roots");
        final ManagedPostgresBuilder builder = ManagedPostgres.temporary()
                .name("temp-db")
                .version("16.4")
                .runtime(RuntimeSource.existing(runtime.runtimeDirectory()));
        final ManagedPostgresBuilder configured =
                ManagedPostgresConfigurer.of(builder).storage(new Storage(temporaryRoot, true));
        final RunningPostgres postgres = configured
                .credentials(Credentials.of("postgres", Secret.of("test-password")))
                .start();
        final Path clusterRoot;
        try {
            clusterRoot = singleClusterRoot(temporaryRoot);
            assertThat(postgres.status()).isEqualTo(PostgresStatus.RUNNING);
            assertThat(ScenarioMetadata.metadataPath(clusterRoot)).isRegularFile();
        } finally {
            postgres.close();
        }

        assertThat(Files.readString(callLog)).contains("stop");
        assertThat(clusterRoot).doesNotExist();
    }

    private static Path singleClusterRoot(final Path temporaryRoot) throws IOException {
        final Path clusterRoot;
        try (Stream<Path> children = Files.list(temporaryRoot)) {
            clusterRoot = children.filter(Files::isDirectory)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("temporary cluster root not found"));
        }

        return clusterRoot;
    }
}
