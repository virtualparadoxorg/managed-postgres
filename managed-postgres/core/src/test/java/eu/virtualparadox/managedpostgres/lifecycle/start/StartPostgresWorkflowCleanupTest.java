package eu.virtualparadox.managedpostgres.lifecycle.start;

import static org.assertj.core.api.Assertions.assertThat;

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
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.FakePostgresRuntime;
import eu.virtualparadox.managedpostgres.runtime.ExistingRuntimeResolver;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class StartPostgresWorkflowCleanupTest {

    private static final Duration LIFECYCLE_TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    private Path temporaryDirectory;

    StartPostgresWorkflowCleanupTest() {}

    @Test
    void startupRotatesOversizedPostgresLogBeforeStartingProcess() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts();
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final Path stateDirectory = storageRoot.resolve("state");
        final Path postgresLog = stateDirectory.resolve("postgres.log");
        Files.createDirectories(stateDirectory);
        Files.writeString(postgresLog, "0123456789", StandardCharsets.UTF_8);

        try (RunningPostgres handle = workflow()
                .start(configuration(
                        new Storage(storageRoot, false),
                        runtimeDirectory,
                        StopPolicy.STOP_ON_CLOSE,
                        CleanupPolicy.safeDefaults().rotateLogsAboveBytes(10L)))) {
            assertThat(handle.status()).isEqualTo(PostgresStatus.RUNNING);
        }

        assertThat(postgresLog).doesNotExist();
        assertThat(Files.readString(stateDirectory.resolve("postgres.log.1"), StandardCharsets.UTF_8))
                .isEqualTo("0123456789");
        assertThat(calls()).contains("pg_ctl start");
    }

    @Test
    void closingStartedHandleStopsPostgres() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts();

        try (RunningPostgres handle = workflow()
                .start(configuration(
                        new Storage(temporaryDirectory.resolve("local-postgres"), false),
                        runtimeDirectory,
                        StopPolicy.STOP_ON_CLOSE,
                        CleanupPolicy.safeDefaults()))) {
            assertThat(handle.status()).isEqualTo(PostgresStatus.RUNNING);
        }

        assertThat(calls()).contains("pg_ctl start", "pg_ctl stop");
        assertThat(calls().stream().filter("pg_ctl stop"::equals)).hasSize(1);
    }

    @Test
    void closingStartedHandleKeepsRunningWhenStopPolicyRequestsIt() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts();

        try (RunningPostgres handle = workflow()
                .start(configuration(
                        new Storage(temporaryDirectory.resolve("local-postgres"), false),
                        runtimeDirectory,
                        StopPolicy.KEEP_RUNNING,
                        CleanupPolicy.safeDefaults()))) {
            assertThat(handle.status()).isEqualTo(PostgresStatus.RUNNING);
        }

        assertThat(calls()).contains("pg_ctl start");
        assertThat(calls()).doesNotContain("pg_ctl stop");
    }

    @Test
    void cleanupPolicyCanKeepTemporaryClusterAfterClose() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts();
        final Storage storage = new Storage(temporaryDirectory.resolve("temporary-root"), true);
        final Path startedRoot;

        try (RunningPostgres handle = workflow()
                .start(configuration(
                        storage,
                        runtimeDirectory,
                        StopPolicy.STOP_ON_CLOSE,
                        CleanupPolicy.safeDefaults().deleteTemporaryClusterOnClose(false)))) {
            startedRoot = ((StartedPostgresHandle) handle).layout().root();
        }

        assertThat(startedRoot).isDirectory();
        assertThat(calls()).contains("pg_ctl start", "pg_ctl stop");
    }

    private StartPostgresWorkflow workflow() {
        return new StartPostgresWorkflow(
                new ExistingRuntimeResolver(),
                new FileSystemOperationJournal(),
                new PostgresLockService(),
                LIFECYCLE_TIMEOUT);
    }

    private StartPostgresWorkflow.Configuration configuration(
            final Storage storage,
            final Path runtimeDirectory,
            final StopPolicy stopPolicy,
            final CleanupPolicy cleanupPolicy) {
        return new StartPostgresWorkflow.Configuration(
                "app-db",
                "16.4",
                storage,
                RuntimeSource.existing(runtimeDirectory),
                Credentials.of("postgres", Secret.of("test-password")),
                Network.localhostOnly(),
                ClusterBootstrap.defaultCluster(),
                AttachPolicy.CREATE_NEW,
                stopPolicy,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                cleanupPolicy);
    }

    private Path runtimeWithScripts() throws IOException {
        return fakeRuntime().runtimeWithScripts(List.of());
    }

    private List<String> calls() throws IOException {
        return fakeRuntime().calls();
    }

    private FakePostgresRuntime fakeRuntime() {
        return new FakePostgresRuntime(temporaryDirectory);
    }
}
