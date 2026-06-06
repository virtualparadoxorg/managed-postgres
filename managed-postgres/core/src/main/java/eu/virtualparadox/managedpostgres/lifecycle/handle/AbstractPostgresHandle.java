package eu.virtualparadox.managedpostgres.lifecycle.handle;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperation;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreOperation;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared lifecycle handle behavior for managed PostgreSQL instances.
 */
public abstract class AbstractPostgresHandle implements RunningPostgres {

    private final PostgresConnectionInfo connectionInfo;
    private final PostgresBackupOperation backupOperation;
    private final PostgresRestoreOperation restoreOperation;
    private final AtomicReference<PostgresStatus> status;

    /**
     * Creates a AbstractPostgresHandle instance.
     *
     * @param connectionInfo connection info value
     * @param backupOperation backup operation value
     * @param restoreOperation restore operation value
     */
    public AbstractPostgresHandle(
            final PostgresConnectionInfo connectionInfo,
            final PostgresBackupOperation backupOperation,
            final PostgresRestoreOperation restoreOperation) {
        this.connectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        this.backupOperation = Objects.requireNonNull(backupOperation, "backupOperation");
        this.restoreOperation = Objects.requireNonNull(restoreOperation, "restoreOperation");
        status = new AtomicReference<>(PostgresStatus.RUNNING);
    }

    /**
     * Returns connection details.
     *
     * @return connection details
     */
    @Override
    public final PostgresConnectionInfo connectionInfo() {
        return connectionInfo;
    }

    /**
     * Returns the current handle status.
     *
     * @return current handle status
     */
    @Override
    public final PostgresStatus status() {
        return Objects.requireNonNull(status.get(), "status");
    }

    /**
     * Creates a logical backup through the configured backup operation.
     *
     * @param target backup target file
     */
    @Override
    public final void backupTo(final Path target) {
        PostgresHandleBackups.backupTo(status, backupOperation, target);
    }

    /**
     * Restores a logical backup through the configured restore operation.
     *
     * @param backup backup file to restore
     * @param options restore options
     */
    @Override
    public final void restoreFrom(final Path backup, final RestoreOptions options) {
        PostgresHandleRestores.restoreFrom(status, restoreOperation, backup, options);
    }

    /**
     * Returns whether compare and set status.
     *
     * @param expected expected value
     * @param updated updated value
     * @return compare and set status result
     */
    protected final boolean compareAndSetStatus(final PostgresStatus expected, final PostgresStatus updated) {
        return status.compareAndSet(
                Objects.requireNonNull(expected, "expected"), Objects.requireNonNull(updated, "updated"));
    }

    /**
     * Performs the set status operation.
     *
     * @param updated updated value
     */
    protected final void setStatus(final PostgresStatus updated) {
        status.set(Objects.requireNonNull(updated, "updated"));
    }
}
