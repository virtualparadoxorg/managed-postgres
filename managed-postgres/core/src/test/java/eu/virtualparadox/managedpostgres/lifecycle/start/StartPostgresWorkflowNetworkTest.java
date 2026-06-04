package eu.virtualparadox.managedpostgres.lifecycle.start;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.ManagedPostgresBuilder;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.lifecycle.handle.StartedPostgresHandle;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import eu.virtualparadox.managedpostgres.lifecycle.port.AllocatedPort;
import eu.virtualparadox.managedpostgres.lifecycle.port.PortAllocator;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.FakePostgresRuntime;
import eu.virtualparadox.managedpostgres.runtime.ExistingRuntimeResolver;
import eu.virtualparadox.managedpostgres.security.Secret;
import eu.virtualparadox.managedpostgres.spi.ManagedPostgresConfigurer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("PMD.CouplingBetweenObjects")
public final class StartPostgresWorkflowNetworkTest {

    @TempDir
    private Path temporaryDirectory;

    StartPostgresWorkflowNetworkTest() {}

    @Test
    void temporaryStartCreatesLayoutCredentialsConfigMetadataAndStartedHandle() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts();
        final Storage storage = new Storage(temporaryDirectory.resolve("temporary-root"), true);

        try (RunningPostgres handle = workflow().start(configuration(storage, runtimeDirectory))) {
            assertThat(handle).isInstanceOf(StartedPostgresHandle.class);
            final PostgresLayout startedLayout = ((StartedPostgresHandle) handle).layout();
            assertThat(handle.status()).isEqualTo(PostgresStatus.RUNNING);
            assertThat(handle.connectionInfo().host()).isEqualTo("127.0.0.1");
            assertThat(startedLayout.root())
                    .startsWith(storage.path().toAbsolutePath().normalize());
            assertThat(Files.isRegularFile(startedLayout.dataDirectory().resolve("postgresql.conf")))
                    .isTrue();
            assertThat(Files.isRegularFile(startedLayout.dataDirectory().resolve("pg_hba.conf")))
                    .isTrue();
            assertThat(Files.isRegularFile(startedLayout.stateDirectory().resolve("credentials.properties")))
                    .isTrue();
            assertThat(Files.isRegularFile(startedLayout.metadataPath())).isTrue();
            assertThat(Files.readString(
                            startedLayout.dataDirectory().resolve("postgresql.conf"), StandardCharsets.UTF_8))
                    .contains("listen_addresses='127.0.0.1'")
                    .contains("password_encryption='scram-sha-256'")
                    .containsPattern("port=\\d+");
            assertThat(Files.readString(startedLayout.dataDirectory().resolve("pg_hba.conf"), StandardCharsets.UTF_8))
                    .contains("scram-sha-256")
                    .contains("127.0.0.1/32");
            assertThat(Files.exists(startedLayout.stateDirectory().resolve("initdb-password.txt")))
                    .isFalse();
        }

        assertThat(calls()).contains("initdb", "pg_ctl start", "pg_isready");
    }

    @Test
    void startUsesConfiguredFixedLoopbackPort() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts();
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final int fixedPort = availablePort();

        try (ManagedPostgres postgres = managedPostgres(
                        storageRoot, runtimeDirectory, Network.localhostOnly().port(fixedPort));
                RunningPostgres handle = postgres.start()) {
            assertThat(handle.connectionInfo().port()).isEqualTo(fixedPort);
            assertThat(Files.readString(storageRoot.resolve("data/postgresql.conf"), StandardCharsets.UTF_8))
                    .contains("port=" + fixedPort);
            assertThat(Files.readString(storageRoot.resolve("state/metadata.json"), StandardCharsets.UTF_8))
                    .contains("\"port\":" + fixedPort);
        }
    }

    @Test
    void preferredPortUsesConfiguredPortWhenAvailable() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts();
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final int preferredPort = availablePort();

        try (ManagedPostgres postgres = managedPostgres(
                        storageRoot,
                        runtimeDirectory,
                        Network.localhostOnly().preferredPort(preferredPort).fallbackToRandom());
                RunningPostgres handle = postgres.start()) {
            assertThat(handle.connectionInfo().host()).isEqualTo("127.0.0.1");
            assertThat(handle.connectionInfo().port()).isEqualTo(preferredPort);
        }
    }

    @Test
    void stableRandomPortIsReusedForPersistentStorage() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts();
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final Storage storage = new Storage(storageRoot, false);
        final Network network = Network.localhostOnly().stableRandomPort();
        final int firstPort;

        try (ManagedPostgres postgres = managedPostgres(storageRoot, runtimeDirectory, network);
                RunningPostgres handle = postgres.start()) {
            firstPort = handle.connectionInfo().port();
        }

        try (ManagedPostgres postgres = managedPostgres(storage.path(), runtimeDirectory, network);
                RunningPostgres handle = postgres.start()) {
            assertThat(handle.connectionInfo().port()).isEqualTo(firstPort);
        }
    }

    private static ManagedPostgres managedPostgres(
            final Path storageRoot, final Path runtimeDirectory, final Network network) {
        final ManagedPostgresBuilder builder = ManagedPostgres.local()
                .version("16.4")
                .storage(new Storage(storageRoot, false))
                .runtime(RuntimeSource.existing(runtimeDirectory));
        return ManagedPostgresConfigurer.of(builder).network(network).build();
    }

    private Path runtimeWithScripts() throws IOException {
        return new FakePostgresRuntime(temporaryDirectory).runtimeWithScripts(List.of());
    }

    private List<String> calls() throws IOException {
        return new FakePostgresRuntime(temporaryDirectory).calls();
    }

    private static StartPostgresWorkflow workflow() {
        return new StartPostgresWorkflow(
                new ExistingRuntimeResolver(),
                new FileSystemOperationJournal(),
                new PostgresLockService(),
                Duration.ofSeconds(10));
    }

    private static StartPostgresWorkflow.Configuration configuration(
            final Storage storage, final Path runtimeDirectory) {
        return new StartPostgresWorkflow.Configuration(
                "app-db",
                "16.4",
                storage,
                RuntimeSource.existing(runtimeDirectory),
                Credentials.of("postgres", Secret.of("test-password")),
                Network.localhostOnly(),
                ClusterBootstrap.defaultCluster(),
                AttachPolicy.CREATE_NEW,
                StopPolicy.STOP_ON_CLOSE,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults());
    }

    private static int availablePort() {
        try (AllocatedPort allocatedPort = new PortAllocator().allocateRandom()) {
            return allocatedPort.port();
        }
    }
}
