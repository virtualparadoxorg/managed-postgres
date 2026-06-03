package eu.virtualparadox.managedpostgres.lifecycle.handle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.exception.PostgresBackupException;
import eu.virtualparadox.managedpostgres.exception.PostgresShutdownException;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperation;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreOperation;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartedPostgresStopper;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.FakePostgresRuntime;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresLayoutFixture;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.start.Script;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class StartedPostgresHandleTest {

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    private Path temporaryDirectory;

    StartedPostgresHandleTest() {}

    @Test
    void startedHandleStopIsIdempotentAfterSuccessfulShutdown() throws IOException {
        final FakePostgresRuntime fakeRuntime = new FakePostgresRuntime(temporaryDirectory);
        final Path runtimeDirectory = fakeRuntime.runtimeWithScripts(List.of());

        try (StartedPostgresHandle handle = handle(runtimeDirectory, StopPolicy.STOP_ON_CLOSE)) {
            handle.stop();
            handle.stop();

            assertThat(handle.status()).isEqualTo(PostgresStatus.STOPPED);
        }
        assertThat(fakeRuntime.calls().stream().filter("pg_ctl stop"::equals)).hasSize(1);
    }

    @Test
    void startedHandleMarksFailedWhenShutdownCommandReturnsFailure() throws IOException {
        final FakePostgresRuntime fakeRuntime = new FakePostgresRuntime(temporaryDirectory);
        final Path runtimeDirectory = fakeRuntime.runtimeWithScripts(List.of(new Script(
                "pg_ctl",
                "printf '%s\\n' 'pg_ctl stop' >> " + FakePostgresRuntime.shellQuote(fakeRuntime.callsPath())
                        + "\nexit 1\n")));

        try (StartedPostgresHandle handle = handle(runtimeDirectory, StopPolicy.STOP_ON_CLOSE)) {
            assertThatThrownBy(handle::stop)
                    .isInstanceOf(PostgresShutdownException.class)
                    .hasMessageContaining("pg_ctl stop failed");
            assertThat(handle.status()).isEqualTo(PostgresStatus.FAILED);
        }
    }

    @Test
    void startedHandleCloseCanKeepProcessRunning() throws IOException {
        final FakePostgresRuntime fakeRuntime = new FakePostgresRuntime(temporaryDirectory);
        final Path runtimeDirectory = fakeRuntime.runtimeWithScripts(List.of());

        try (StartedPostgresHandle handle = handle(runtimeDirectory, StopPolicy.KEEP_RUNNING)) {
            closeAction(handle).run();

            assertThat(handle.status()).isEqualTo(PostgresStatus.RUNNING);
            assertThat(fakeRuntime.calls()).isEmpty();
        }
    }

    @Test
    void startedHandleCloseDeletesTemporaryClusterAfterSuccessfulStop() throws IOException {
        final FakePostgresRuntime fakeRuntime = new FakePostgresRuntime(temporaryDirectory);
        final Path runtimeDirectory = fakeRuntime.runtimeWithScripts(List.of());
        final PostgresLayout layout =
                PostgresLayoutFixture.createdLayout(temporaryDirectory.resolve("temporary-storage"));

        closeAction(handle(runtimeDirectory, StopPolicy.STOP_ON_CLOSE, layout, true))
                .run();

        assertThat(layout.root()).doesNotExist();
    }

    @Test
    void startedHandleBackupDelegatesWhenRunning() throws IOException {
        final FakePostgresRuntime fakeRuntime = new FakePostgresRuntime(temporaryDirectory);
        final Path runtimeDirectory = fakeRuntime.runtimeWithScripts(List.of());
        final AtomicReference<Path> backupTarget = new AtomicReference<>();
        final Path target = temporaryDirectory.resolve("backup.dump");

        try (StartedPostgresHandle handle = handle(
                new HandleSettings(
                        runtimeDirectory,
                        StopPolicy.KEEP_RUNNING,
                        PostgresLayoutFixture.createdLayout(temporaryDirectory.resolve("backup-storage")),
                        false),
                backupTarget::set)) {
            handle.backupTo(target);
        }

        assertThat(backupTarget).hasValue(target);
    }

    @Test
    void startedHandleBackupFailsWhenStopped() throws IOException {
        final FakePostgresRuntime fakeRuntime = new FakePostgresRuntime(temporaryDirectory);
        final Path runtimeDirectory = fakeRuntime.runtimeWithScripts(List.of());
        final AtomicReference<Path> backupTarget = new AtomicReference<>();

        try (StartedPostgresHandle handle = handle(
                new HandleSettings(
                        runtimeDirectory,
                        StopPolicy.KEEP_RUNNING,
                        PostgresLayoutFixture.createdLayout(temporaryDirectory.resolve("stopped-storage")),
                        false),
                backupTarget::set)) {
            handle.stop();

            assertThatThrownBy(() -> handle.backupTo(temporaryDirectory.resolve("backup.dump")))
                    .isInstanceOf(PostgresBackupException.class)
                    .hasMessageContaining("not running")
                    .satisfies(throwable -> assertThat(((PostgresBackupException) throwable)
                                    .diagnosticReport()
                                    .renderText())
                            .contains("STOPPED"));
        }

        assertThat(backupTarget).hasNullValue();
    }

    private StartedPostgresHandle handle(final Path runtimeDirectory, final StopPolicy stopPolicy) {
        final PostgresLayout layout = PostgresLayoutFixture.createdLayout(temporaryDirectory.resolve("storage"));

        return handle(new HandleSettings(runtimeDirectory, stopPolicy, layout, false));
    }

    private StartedPostgresHandle handle(
            final Path runtimeDirectory,
            final StopPolicy stopPolicy,
            final PostgresLayout layout,
            final boolean deleteOnClose) {
        return handle(new HandleSettings(runtimeDirectory, stopPolicy, layout, deleteOnClose));
    }

    private StartedPostgresHandle handle(final HandleSettings settings) {
        return handle(settings, target -> {
            // Backup is not part of shutdown-focused tests.
        });
    }

    private StartedPostgresHandle handle(final HandleSettings settings, final PostgresBackupOperation backupOperation) {
        return handle(settings, backupOperation, (backup, options) -> {
            // Restore is not part of backup/shutdown-focused tests.
        });
    }

    private StartedPostgresHandle handle(
            final HandleSettings settings,
            final PostgresBackupOperation backupOperation,
            final PostgresRestoreOperation restoreOperation) {
        final HandleSettings checkedSettings = Objects.requireNonNull(settings, "settings");

        return new StartedPostgresHandle(
                new PostgresConnectionInfo("127.0.0.1", 15432, "postgres", "postgres", Secret.redacted()),
                new StartedPostgresHandle.Dependencies(
                        checkedSettings.layout(),
                        new StartedPostgresStopper(
                                checkedSettings.runtimeDirectory(), new CommandRunner(), SHUTDOWN_TIMEOUT),
                        checkedSettings.stopPolicy(),
                        checkedSettings.deleteOnClose(),
                        backupOperation,
                        restoreOperation,
                        new StartupTelemetry(Duration.ZERO, 0)));
    }

    private static Runnable closeAction(final StartedPostgresHandle handle) {
        return handle::close;
    }

    private record HandleSettings(
            Path runtimeDirectory, StopPolicy stopPolicy, PostgresLayout layout, boolean deleteOnClose) {

        private HandleSettings {
            Objects.requireNonNull(runtimeDirectory, "runtimeDirectory");
            Objects.requireNonNull(stopPolicy, "stopPolicy");
            Objects.requireNonNull(layout, "layout");
        }
    }
}
