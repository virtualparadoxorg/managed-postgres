package eu.virtualparadox.managedpostgres.lifecycle.start.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import eu.virtualparadox.managedpostgres.config.postgresql.Resources;
import eu.virtualparadox.managedpostgres.exception.PostgresStartupException;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.FakePostgresRuntime;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.start.Script;
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

public final class StartPostgresWorkflowConfigurationTest {

    private static final Duration LIFECYCLE_TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    private Path temporaryDirectory;

    StartPostgresWorkflowConfigurationTest() {
    }

    @Test
    void startupWritesConfiguredResourcePresetSettings() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of());
        final Path storageRoot = temporaryDirectory.resolve("configured-postgres");
        final StartPostgresWorkflow.Configuration configuration = new StartPostgresWorkflow.Configuration(
                "app-db",
                "16.4",
                new Storage(storageRoot, false),
                RuntimeSource.existing(runtimeDirectory),
                Credentials.of("postgres", Secret.of("test-password")),
                Network.localhostOnly(),
                ClusterBootstrap.defaultCluster(),
                Resources.small().maxConnections(40),
                AttachPolicy.CREATE_NEW,
                StopPolicy.STOP_ON_CLOSE,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults());

        try (RunningPostgres handle = workflow().start(configuration)) {
            assertThat(handle.status()).isEqualTo(PostgresStatus.RUNNING);
        }

        assertThat(Files.readString(storageRoot.resolve("data/postgresql.conf"), StandardCharsets.UTF_8))
                .contains("max_connections=40")
                .contains("shared_buffers='128MB'");
    }

    @Test
    void invalidPostgresqlVersionFailsBeforeStartingProcess() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of());

        assertThatThrownBy(() -> workflow().start(configuration(
                new Storage(temporaryDirectory.resolve("local-postgres"), false),
                runtimeDirectory,
                "sixteen")))
                .isInstanceOf(PostgresStartupException.class)
                .satisfies(throwable -> assertThat(((PostgresStartupException) throwable).diagnosticReport().renderText())
                        .contains("startup-configuration")
                        .contains("postgresqlVersion"));
        assertThat(calls()).doesNotContain("pg_ctl start");
    }

    @Test
    void dirtyDataDirectoryWithoutPgVersionFailsBeforeStart() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of());
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final Path dataDirectory = storageRoot.resolve("data");
        Files.createDirectories(dataDirectory);
        Files.writeString(dataDirectory.resolve("stray-file"), "not initialized", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> workflow().start(configuration(new Storage(storageRoot, false), runtimeDirectory, "16.4")))
                .isInstanceOf(PostgresStartupException.class)
                .satisfies(throwable -> assertThat(((PostgresStartupException) throwable).diagnosticReport().renderText())
                        .contains("data-directory")
                        .contains("PG_VERSION"));
        assertThat(calls()).doesNotContain("pg_ctl");
    }

    @Test
    void initdbUsesPasswordFileAndScramAuthenticationWithoutLeakingSecretInCommand() throws IOException {
        final Path runtimeDirectory = runtimeWithScripts(List.of(new Script(
                "initdb",
                "printf '%s %s\\n' initdb \"$*\" >> " + shellQuote(callsPath()) + "\n"
                        + "while [ \"$#\" -gt 0 ]; do\n"
                        + "  if [ \"$1\" = '-D' ]; then\n"
                        + "    shift\n"
                        + "    mkdir -p \"$1\"\n"
                        + "    printf '%s\\n' 16 > \"$1/PG_VERSION\"\n"
                        + "  fi\n"
                        + "  shift\n"
                        + "done\n"
                        + "exit 0\n")));

        workflow().start(configuration(new Storage(temporaryDirectory.resolve("local-postgres"), false), runtimeDirectory, "16.4"));

        assertThat(calls())
                .anySatisfy(call -> assertThat(call)
                        .contains("--auth-host=scram-sha-256")
                        .contains("--auth-local=scram-sha-256")
                        .contains("--pwfile=")
                        .doesNotContain("test-password"));
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
            final String postgresqlVersion) {
        return new StartPostgresWorkflow.Configuration(
                "app-db",
                postgresqlVersion,
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

    private Path runtimeWithScripts(final List<Script> overrides) throws IOException {
        return new FakePostgresRuntime(temporaryDirectory).runtimeWithScripts(overrides);
    }

    private List<String> calls() throws IOException {
        return new FakePostgresRuntime(temporaryDirectory).calls();
    }

    private Path callsPath() {
        return new FakePostgresRuntime(temporaryDirectory).callsPath();
    }

    private static String shellQuote(final Path path) {
        return FakePostgresRuntime.shellQuote(path);
    }
}
