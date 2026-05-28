package eu.virtualparadox.managedpostgres.lifecycle.handle;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupFailures;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperation;

/**
 * Shared backup behavior for PostgreSQL handles.
 */
public final class PostgresHandleBackups {

    private PostgresHandleBackups() {
    }

    /**
     * Performs the backup to operation.
     *
     * @param status status value
     * @param backupOperation backup operation value
     * @param target target value
     */
    public static void backupTo(
            final AtomicReference<PostgresStatus> status,
            final PostgresBackupOperation backupOperation,
            final Path target) {
        final AtomicReference<PostgresStatus> checkedStatus = Objects.requireNonNull(status, "status");
        final PostgresBackupOperation checkedBackupOperation =
                Objects.requireNonNull(backupOperation, "backupOperation");
        final Path checkedTarget = Objects.requireNonNull(target, "target");
        final PostgresStatus currentStatus = Objects.requireNonNull(checkedStatus.get(), "status");
        if (currentStatus != PostgresStatus.RUNNING) {
            throw PostgresBackupFailures.notRunning(currentStatus);
        }
        checkedBackupOperation.backupTo(checkedTarget);
    }
}
