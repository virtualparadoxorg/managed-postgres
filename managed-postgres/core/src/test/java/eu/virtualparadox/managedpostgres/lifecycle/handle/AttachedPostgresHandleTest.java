package eu.virtualparadox.managedpostgres.lifecycle.handle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.exception.PostgresBackupException;
import eu.virtualparadox.managedpostgres.exception.PostgresRestoreException;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperation;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreOperation;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public final class AttachedPostgresHandleTest {

    AttachedPostgresHandleTest() {}

    @Test
    void attachedHandleCloseFollowsKeepRunningStopPolicy() {
        final AtomicInteger stopCalls = new AtomicInteger();
        try (AttachedPostgresHandle handle = new AttachedPostgresHandle(
                connectionInfo(),
                StopPolicy.KEEP_RUNNING,
                stopCalls::incrementAndGet,
                noopBackupOperation(),
                noopRestoreOperation())) {
            closeAction(handle).run();

            assertThat(stopCalls).hasValue(0);
            assertThat(handle.status()).isEqualTo(PostgresStatus.RUNNING);
        }
    }

    @Test
    void attachedHandleCloseFollowsStopOnClosePolicy() {
        final AtomicInteger stopCalls = new AtomicInteger();
        try (AttachedPostgresHandle handle = new AttachedPostgresHandle(
                connectionInfo(),
                StopPolicy.STOP_ON_CLOSE,
                stopCalls::incrementAndGet,
                noopBackupOperation(),
                noopRestoreOperation())) {
            closeAction(handle).run();
            closeAction(handle).run();

            assertThat(stopCalls).hasValue(1);
            assertThat(handle.status()).isEqualTo(PostgresStatus.STOPPED);
        }
    }

    @Test
    void attachedHandleStopFailureKeepsRunningAndCanBeRetried() {
        final AtomicInteger stopCalls = new AtomicInteger();
        try (AttachedPostgresHandle handle = new AttachedPostgresHandle(
                connectionInfo(),
                StopPolicy.STOP_ON_CLOSE,
                () -> {
                    if (stopCalls.incrementAndGet() == 1) {
                        throw new IllegalStateException("stop failed");
                    }
                },
                noopBackupOperation(),
                noopRestoreOperation())) {

            assertThatThrownBy(handle::stop)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("stop failed");
            assertThat(handle.status()).isEqualTo(PostgresStatus.RUNNING);

            handle.stop();

            assertThat(stopCalls).hasValue(2);
            assertThat(handle.status()).isEqualTo(PostgresStatus.STOPPED);
        }
    }

    @Test
    void attachedHandleExplicitStopRunsWhenClosePolicyKeepsRunning() {
        final AtomicInteger stopCalls = new AtomicInteger();
        try (AttachedPostgresHandle handle = new AttachedPostgresHandle(
                connectionInfo(),
                StopPolicy.KEEP_RUNNING,
                stopCalls::incrementAndGet,
                noopBackupOperation(),
                noopRestoreOperation())) {

            handle.stop();

            assertThat(stopCalls).hasValue(1);
            assertThat(handle.status()).isEqualTo(PostgresStatus.STOPPED);
        }
    }

    @Test
    void attachedHandleBackupDelegatesWhenRunning() {
        final AtomicReference<Path> backupTarget = new AtomicReference<>();
        final Path target = Path.of("backup.dump");

        try (AttachedPostgresHandle handle = new AttachedPostgresHandle(
                connectionInfo(),
                StopPolicy.KEEP_RUNNING,
                () -> {
                    // This test does not stop the handle.
                },
                backupTarget::set,
                noopRestoreOperation())) {
            handle.backupTo(target);
        }

        assertThat(backupTarget).hasValue(target);
    }

    @Test
    void attachedHandleBackupFailsWhenStopped() {
        final AtomicReference<Path> backupTarget = new AtomicReference<>();

        try (AttachedPostgresHandle handle = new AttachedPostgresHandle(
                connectionInfo(),
                StopPolicy.KEEP_RUNNING,
                () -> {
                    // Stop transitions the handle to stopped.
                },
                backupTarget::set,
                noopRestoreOperation())) {
            handle.stop();

            assertThatThrownBy(() -> handle.backupTo(Path.of("backup.dump")))
                    .isInstanceOf(PostgresBackupException.class)
                    .hasMessageContaining("not running");
        }

        assertThat(backupTarget).hasNullValue();
    }

    @Test
    void attachedHandleRestoreDelegatesWhenRunning() {
        final AtomicReference<Path> restoreBackup = new AtomicReference<>();
        final AtomicReference<RestoreOptions> restoreOptions = new AtomicReference<>();
        final RestoreOptions options =
                RestoreOptions.builder().dropCurrentDatabase(true).build();
        final Path backup = Path.of("backup.dump");

        try (AttachedPostgresHandle handle = new AttachedPostgresHandle(
                connectionInfo(),
                StopPolicy.KEEP_RUNNING,
                () -> {
                    // This test does not stop the handle.
                },
                noopBackupOperation(),
                (target, requestedOptions) -> {
                    restoreBackup.set(target);
                    restoreOptions.set(requestedOptions);
                })) {
            handle.restoreFrom(backup, options);
        }

        assertThat(restoreBackup).hasValue(backup);
        assertThat(restoreOptions).hasValue(options);
    }

    @Test
    void attachedHandleRestoreFailsWhenStopped() {
        final AtomicReference<Path> restoreBackup = new AtomicReference<>();

        try (AttachedPostgresHandle handle = new AttachedPostgresHandle(
                connectionInfo(),
                StopPolicy.KEEP_RUNNING,
                () -> {
                    // Stop transitions the handle to stopped.
                },
                noopBackupOperation(),
                (target, options) -> restoreBackup.set(target))) {
            handle.stop();

            assertThatThrownBy(() -> handle.restoreFrom(
                            Path.of("backup.dump"),
                            RestoreOptions.builder().dropCurrentDatabase(true).build()))
                    .isInstanceOf(PostgresRestoreException.class)
                    .hasMessageContaining("not running");
        }

        assertThat(restoreBackup).hasNullValue();
    }

    private static Runnable closeAction(final AttachedPostgresHandle handle) {
        return handle::close;
    }

    private static PostgresConnectionInfo connectionInfo() {
        return new PostgresConnectionInfo("127.0.0.1", 15432, "postgres", "postgres", Secret.of("test-password"));
    }

    private static PostgresBackupOperation noopBackupOperation() {
        return target -> {
            // Test handle does not create backups.
        };
    }

    private static PostgresRestoreOperation noopRestoreOperation() {
        return (target, options) -> {
            // Test handle does not restore backups.
        };
    }
}
