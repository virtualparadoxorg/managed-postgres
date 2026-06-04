package eu.virtualparadox.managedpostgres.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.ManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.scenario.support.LoopbackTcpServer;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioJdbcDriver;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioMetadata;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioShell;
import eu.virtualparadox.managedpostgres.security.Secret;
import eu.virtualparadox.managedpostgres.test.FakePostgresRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("driver-manager")
final class FakeRuntimeAttachIT {

    private static final String TEST_PASSWORD = "test-password";

    @TempDir
    private Path temporaryDirectory;

    FakeRuntimeAttachIT() {}

    @Test
    void localStartMetadataExistsSecondStartAttachesWhenHealthy() throws IOException, SQLException {
        final Path callLog = temporaryDirectory.resolve("pg_ctl-calls.log");
        final FakePostgresRuntime runtime = FakePostgresRuntime.create(
                temporaryDirectory.resolve("runtime"), ScenarioShell.recordingPgCtl(callLog));
        final Path storageRoot = temporaryDirectory.resolve("cluster");

        try (RunningPostgres first = localPostgres(storageRoot, runtime).start()) {
            assertThat(first.status()).isEqualTo(PostgresStatus.RUNNING);
            final PostgresInstanceMetadata metadata = ScenarioMetadata.require(storageRoot);

            try (LoopbackTcpServer server = LoopbackTcpServer.open(metadata.host(), metadata.port());
                    ScenarioJdbcDriver driver = ScenarioJdbcDriver.register(metadata);
                    RunningPostgres second =
                            localPostgres(storageRoot, runtime).reuseExisting().start()) {
                server.assertHealthy();
                assertThat(second.status()).isEqualTo(PostgresStatus.RUNNING);
                assertThat(second.connectionInfo().port()).isEqualTo(metadata.port());
                assertThat(driver.connections()).isPositive();
            }

            assertThat(Files.readAllLines(callLog)).filteredOn("start"::equals).hasSize(1);
            assertThat(ScenarioMetadata.staleMetadataPath(storageRoot)).doesNotExist();
        }
    }

    private static ManagedPostgresBuilder localPostgres(final Path storageRoot, final FakePostgresRuntime runtime) {
        return ManagedPostgres.local()
                .name("app-db")
                .version("16.4")
                .runtime(RuntimeSource.existing(runtime.runtimeDirectory()))
                .storageProjectLocal(storageRoot)
                .credentials(Credentials.of("postgres", Secret.of(TEST_PASSWORD)));
    }
}
