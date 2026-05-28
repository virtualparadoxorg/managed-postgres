package eu.virtualparadox.managedpostgres.lifecycle.handle;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.exception.PostgresShutdownException;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperation;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.restore.PostgresRestoreOperation;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartedPostgresStopper;
import eu.virtualparadox.managedpostgres.lifecycle.layout.TemporaryClusterCleanup;

/**
 * Handle for a PostgreSQL instance started by this JVM.
 */
public final class StartedPostgresHandle extends AbstractPostgresHandle {

    private final PostgresLayout layout;
    private final StartedPostgresStopper stopper;
    private final StopPolicy stopPolicy;
    private final boolean deleteOnClose;
    private final StartupTelemetry startupTelemetry;

    /**
     * Creates a started PostgreSQL handle.
     *
     * @param connectionInfo connection details
     * @param dependencies started handle dependencies
     */
    public StartedPostgresHandle(
            final PostgresConnectionInfo connectionInfo,
            final Dependencies dependencies) {
        super(connectionInfo, backupOperation(dependencies), restoreOperation(dependencies));
        final Dependencies checkedDependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.layout = checkedDependencies.layout();
        this.stopper = checkedDependencies.stopper();
        this.stopPolicy = checkedDependencies.stopPolicy();
        deleteOnClose = checkedDependencies.deleteOnClose();
        startupTelemetry = checkedDependencies.startupTelemetry();
    }

    /**
     * Marks this handle as stopped.
     */
    @Override
    public final void stop() {
        if (compareAndSetStatus(PostgresStatus.RUNNING, PostgresStatus.STOPPED)) {
            stopPostgres();
        }
    }

    /**
     * Closes this handle.
     */
    @Override
    public final void close() {
        if (stopPolicy == StopPolicy.STOP_ON_CLOSE) {
            stop();
            deleteTemporaryClusterAfterStop();
        }
    }

    /**
     * Returns the layout result.
     *
     * @return layout result
     */
    public PostgresLayout layout() {
        return layout;
    }

    /**
     * Returns immutable startup telemetry captured for this started instance.
     *
     * @return immutable startup telemetry
     */
    public StartupTelemetry startupTelemetry() {
        return startupTelemetry;
    }

    private void stopPostgres() {
        try {
            stopper.stop(layout);
        } catch (final PostgresShutdownException exception) {
            setStatus(PostgresStatus.FAILED);
            throw exception;
        }
    }

    private void deleteTemporaryClusterAfterStop() {
        if (deleteOnClose && status() == PostgresStatus.STOPPED) {
            try {
                TemporaryClusterCleanup.delete(layout);
            } catch (final PostgresShutdownException exception) {
                setStatus(PostgresStatus.FAILED);
                throw exception;
            }
        }
    }

    private static PostgresBackupOperation backupOperation(final Dependencies dependencies) {
        return Objects.requireNonNull(dependencies, "dependencies").backupOperation();
    }

    private static PostgresRestoreOperation restoreOperation(final Dependencies dependencies) {
        return Objects.requireNonNull(dependencies, "dependencies").restoreOperation();
    }

    /**
     * Dependencies required by a started PostgreSQL handle.
     *
     * @param layout PostgreSQL filesystem layout
     * @param stopper PostgreSQL stop command operation
     * @param stopPolicy close-time stop policy
     * @param deleteOnClose whether close deletes the owned temporary cluster after stopping
     * @param backupOperation logical backup operation
     * @param restoreOperation logical restore operation
     * @param startupTelemetry immutable startup telemetry
     */
    public record Dependencies(
            PostgresLayout layout,
            StartedPostgresStopper stopper,
            StopPolicy stopPolicy,
            boolean deleteOnClose,
            PostgresBackupOperation backupOperation,
            PostgresRestoreOperation restoreOperation,
            StartupTelemetry startupTelemetry) {

        /**
         * Validates started handle dependencies.
         *
         * @param layout PostgreSQL filesystem layout
         * @param stopper PostgreSQL stop command operation
         * @param stopPolicy close-time stop policy
         * @param deleteOnClose whether close deletes the owned temporary cluster after stopping
         * @param backupOperation logical backup operation
         * @param restoreOperation logical restore operation
         * @param startupTelemetry immutable startup telemetry
         */
        public Dependencies {
            Objects.requireNonNull(layout, "layout");
            Objects.requireNonNull(stopper, "stopper");
            Objects.requireNonNull(stopPolicy, "stopPolicy");
            Objects.requireNonNull(backupOperation, "backupOperation");
            Objects.requireNonNull(restoreOperation, "restoreOperation");
            Objects.requireNonNull(startupTelemetry, "startupTelemetry");
        }
    }
}
