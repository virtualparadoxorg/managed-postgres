package eu.virtualparadox.managedpostgres.lifecycle.attach;

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
import eu.virtualparadox.managedpostgres.exception.PostgresShutdownException;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.handle.PostgresHandleOperationProviders;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.FakePostgresRuntime;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresLayoutFixture;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresMetadataFixture;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.restore.UnexpectedBackupOperationProvider;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.start.Script;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class PostgresAttachedHandleFactoryTest {

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    private Path temporaryDirectory;

    PostgresAttachedHandleFactoryTest() {}

    @Test
    void attachedHandleExposesMetadataConnectionInfoAndStopsWithPgCtl() throws IOException {
        final FakePostgresRuntime fakeRuntime = new FakePostgresRuntime(temporaryDirectory);
        final Path runtimeDirectory = fakeRuntime.runtimeWithScripts(List.of());
        final PostgresLayout layout = PostgresLayoutFixture.createdLayout(temporaryDirectory.resolve("storage"));

        try (RunningPostgres handle = attachedHandle(runtimeDirectory, layout, StopPolicy.STOP_ON_CLOSE)) {
            assertThat(handle.connectionInfo().host()).isEqualTo("127.0.0.1");
            assertThat(handle.connectionInfo().port()).isEqualTo(15432);
            assertThat(handle.connectionInfo().database()).isEqualTo("postgres");
            assertThat(handle.connectionInfo().username()).isEqualTo("postgres");

            handle.stop();
            handle.stop();

            assertThat(handle.status()).isEqualTo(PostgresStatus.STOPPED);
        }
        assertThat(fakeRuntime.calls().stream().filter("pg_ctl stop"::equals)).hasSize(1);
    }

    @Test
    void attachedHandleReportsPgCtlStopFailureAsShutdownException() throws IOException {
        final FakePostgresRuntime fakeRuntime = new FakePostgresRuntime(temporaryDirectory);
        final Path runtimeDirectory = fakeRuntime.runtimeWithScripts(List.of(new Script(
                "pg_ctl",
                "printf '%s\\n' 'pg_ctl stop' >> "
                        + FakePostgresRuntime.shellQuote(fakeRuntime.callsPath()) + "\n"
                        + "printf '%s\\n' 'permission denied' >&2\n"
                        + "exit 1\n")));
        final PostgresLayout layout = PostgresLayoutFixture.createdLayout(temporaryDirectory.resolve("storage"));

        try (RunningPostgres handle = attachedHandle(runtimeDirectory, layout, StopPolicy.KEEP_RUNNING)) {
            assertThatThrownBy(handle::stop)
                    .isInstanceOf(PostgresShutdownException.class)
                    .hasMessageContaining("pg_ctl stop failed");
            assertThat(handle.status()).isEqualTo(PostgresStatus.RUNNING);
        }
    }

    @Test
    void attachedHandleWrapsPgCtlStartFailureAsShutdownException() {
        final Path runtimeDirectory = temporaryDirectory.resolve("missing-runtime");
        final PostgresLayout layout = PostgresLayoutFixture.createdLayout(temporaryDirectory.resolve("storage"));

        try (RunningPostgres handle = attachedHandle(runtimeDirectory, layout, StopPolicy.KEEP_RUNNING)) {
            assertThatThrownBy(handle::stop)
                    .isInstanceOf(PostgresShutdownException.class)
                    .hasMessageContaining("pg_ctl stop command failed");
            assertThat(handle.status()).isEqualTo(PostgresStatus.RUNNING);
        }
    }

    private RunningPostgres attachedHandle(
            final Path runtimeDirectory, final PostgresLayout layout, final StopPolicy stopPolicy) {
        return new PostgresAttachedHandleFactory(
                        new CommandRunner(),
                        SHUTDOWN_TIMEOUT,
                        PostgresHandleOperationProviders.unsupportedRestore(
                                UnexpectedBackupOperationProvider.unexpectedBackupProvider()))
                .attachedHandle(
                        PostgresMetadataFixture.metadata(layout.dataDirectory(), 15432),
                        configuration(temporaryDirectory.resolve("storage"), runtimeDirectory, stopPolicy),
                        layout,
                        runtimeDirectory);
    }

    private static StartPostgresWorkflow.Configuration configuration(
            final Path storageRoot, final Path runtimeDirectory, final StopPolicy stopPolicy) {
        return new StartPostgresWorkflow.Configuration(
                "app-db",
                "16.4",
                new Storage(storageRoot, false),
                RuntimeSource.existing(runtimeDirectory),
                Credentials.of("postgres", Secret.of("test-password")),
                Network.localhostOnly(),
                ClusterBootstrap.defaultCluster(),
                AttachPolicy.ATTACH_IF_COMPATIBLE,
                stopPolicy,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults());
    }
}
