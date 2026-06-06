package eu.virtualparadox.managedpostgres.lifecycle.handle;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreFailures;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreOperation;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared restore behavior for PostgreSQL handles.
 */
public final class PostgresHandleRestores {

    private PostgresHandleRestores() {}

    /**
     * Performs the restore from operation.
     *
     * @param status status value
     * @param restoreOperation restore operation value
     * @param backup backup value
     * @param options options value
     */
    public static void restoreFrom(
            final AtomicReference<PostgresStatus> status,
            final PostgresRestoreOperation restoreOperation,
            final Path backup,
            final RestoreOptions options) {
        final AtomicReference<PostgresStatus> checkedStatus = Objects.requireNonNull(status, "status");
        final PostgresRestoreOperation checkedRestoreOperation =
                Objects.requireNonNull(restoreOperation, "restoreOperation");
        final Path checkedBackup = Objects.requireNonNull(backup, "backup");
        final RestoreOptions checkedOptions = Objects.requireNonNull(options, "options");
        final PostgresStatus currentStatus = Objects.requireNonNull(checkedStatus.get(), "status");
        if (currentStatus != PostgresStatus.RUNNING) {
            throw PostgresRestoreFailures.notRunning(currentStatus);
        }
        if (!checkedOptions.dropCurrentDatabase()) {
            throw PostgresRestoreFailures.dropCurrentDatabaseRequired();
        }
        checkedRestoreOperation.restoreFrom(checkedBackup, checkedOptions);
    }
}
