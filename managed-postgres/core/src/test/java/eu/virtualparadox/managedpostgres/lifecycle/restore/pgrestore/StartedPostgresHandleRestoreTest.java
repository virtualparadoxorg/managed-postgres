package eu.virtualparadox.managedpostgres.lifecycle.restore.pgrestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.handle.StartedPostgresHandle;
import eu.virtualparadox.managedpostgres.lifecycle.handle.StartupTelemetry;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreOperation;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartedPostgresStopper;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.FakePostgresRuntime;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresLayoutFixture;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class StartedPostgresHandleRestoreTest {

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    private Path temporaryDirectory;

    StartedPostgresHandleRestoreTest() {}

    @Test
    void startedHandleRestoreDelegatesWhenRunning() throws IOException {
        final Path runtimeDirectory = runtimeDirectory();
        final AtomicReference<Path> restoreBackup = new AtomicReference<>();
        final AtomicReference<RestoreOptions> restoreOptions = new AtomicReference<>();
        final Path backup = temporaryDirectory.resolve("backup.dump");
        final RestoreOptions options = RestoreOptions.builder()
                .dropCurrentDatabase(true)
                .createSafetyBackup(true)
                .build();

        try (StartedPostgresHandle handle = handle("restore-storage", runtimeDirectory, (target, requestedOptions) -> {
            restoreBackup.set(target);
            restoreOptions.set(requestedOptions);
        })) {
            handle.restoreFrom(backup, options);
        }

        assertThat(restoreBackup).hasValue(backup);
        assertThat(restoreOptions).hasValue(options);
    }

    @Test
    void startedHandleRestoreRejectsNonDestructiveOptionsBeforeDelegation() throws IOException {
        final AtomicReference<Path> restoreBackup = new AtomicReference<>();

        try (StartedPostgresHandle handle =
                handle("restore-policy-storage", runtimeDirectory(), (target, options) -> restoreBackup.set(target))) {

            assertThatThrownBy(() -> handle.restoreFrom(
                            temporaryDirectory.resolve("backup.dump"),
                            RestoreOptions.builder().build()))
                    .isInstanceOf(PostgresRestoreException.class)
                    .hasMessageContaining("dropCurrentDatabase");
        }

        assertThat(restoreBackup).hasNullValue();
    }

    @Test
    void startedHandleRestoreFailsWhenStopped() throws IOException {
        final AtomicReference<Path> restoreBackup = new AtomicReference<>();

        try (StartedPostgresHandle handle =
                handle("stopped-restore-storage", runtimeDirectory(), (target, options) -> restoreBackup.set(target))) {
            handle.stop();

            assertThatThrownBy(() -> handle.restoreFrom(
                            temporaryDirectory.resolve("backup.dump"),
                            RestoreOptions.builder().dropCurrentDatabase(true).build()))
                    .isInstanceOf(PostgresRestoreException.class)
                    .hasMessageContaining("not running")
                    .satisfies(throwable -> assertThat(((PostgresRestoreException) throwable)
                                    .diagnosticReport()
                                    .renderText())
                            .contains("STOPPED"));
        }

        assertThat(restoreBackup).hasNullValue();
    }

    private Path runtimeDirectory() throws IOException {
        return new FakePostgresRuntime(temporaryDirectory).runtimeWithScripts(List.of());
    }

    private StartedPostgresHandle handle(
            final String storageName, final Path runtimeDirectory, final PostgresRestoreOperation restoreOperation) {
        final PostgresLayout layout = PostgresLayoutFixture.createdLayout(temporaryDirectory.resolve(storageName));

        return new StartedPostgresHandle(
                new PostgresConnectionInfo("127.0.0.1", 15432, "postgres", "postgres", Secret.redacted()),
                new StartedPostgresHandle.Dependencies(
                        layout,
                        new StartedPostgresStopper(
                                Objects.requireNonNull(runtimeDirectory, "runtimeDirectory"),
                                new CommandRunner(),
                                SHUTDOWN_TIMEOUT),
                        StopPolicy.KEEP_RUNNING,
                        false,
                        target -> {
                            // Backup is not part of restore-focused tests.
                        },
                        restoreOperation,
                        new StartupTelemetry(Duration.ZERO, 0)));
    }
}
