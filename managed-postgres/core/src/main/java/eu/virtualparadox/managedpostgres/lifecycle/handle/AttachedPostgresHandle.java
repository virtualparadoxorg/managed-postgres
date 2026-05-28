package eu.virtualparadox.managedpostgres.lifecycle.handle;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperation;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreOperation;

/**
 * Handle for a PostgreSQL instance attached by this JVM.
 */
public final class AttachedPostgresHandle extends AbstractPostgresHandle {

    private final StopPolicy stopPolicy;
    private final Runnable stopAction;
    private final AtomicBoolean stopInProgress;

    /**
     * Creates an attached PostgreSQL handle.
     *
     * @param connectionInfo connection details
     * @param stopPolicy close-time stop policy
     * @param stopAction explicit stop action
     * @param backupOperation logical backup operation
     * @param restoreOperation logical restore operation
     */
    public AttachedPostgresHandle(
            final PostgresConnectionInfo connectionInfo,
            final StopPolicy stopPolicy,
            final Runnable stopAction,
            final PostgresBackupOperation backupOperation,
            final PostgresRestoreOperation restoreOperation) {
        super(connectionInfo, backupOperation, restoreOperation);
        this.stopPolicy = Objects.requireNonNull(stopPolicy, "stopPolicy");
        this.stopAction = Objects.requireNonNull(stopAction, "stopAction");
        stopInProgress = new AtomicBoolean();
    }

    /**
     * Explicitly stops the attached PostgreSQL instance.
     */
    @Override
    public final void stop() {
        if (status() == PostgresStatus.RUNNING && stopInProgress.compareAndSet(false, true)) {
            try {
                stopAction.run();
                setStatus(PostgresStatus.STOPPED);
            } finally {
                stopInProgress.set(false);
            }
        }
    }

    /**
     * Closes this handle according to the configured close-time stop policy.
     */
    @Override
    public final void close() {
        if (stopPolicy == StopPolicy.STOP_ON_CLOSE) {
            stop();
        }
    }
}
