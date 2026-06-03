package eu.virtualparadox.managedpostgres.lifecycle.attach;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.exception.PostgresShutdownException;
import eu.virtualparadox.managedpostgres.lifecycle.PostgresStartupDiagnostics;
import eu.virtualparadox.managedpostgres.lifecycle.backup.operation.PostgresBackupOperationContext;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.handle.AttachedPostgresHandle;
import eu.virtualparadox.managedpostgres.lifecycle.handle.PostgresApplicationConnection;
import eu.virtualparadox.managedpostgres.lifecycle.handle.PostgresHandleOperationProviders;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.start.PgCtlController;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Creates handles for PostgreSQL instances attached by this JVM.
 */
public final class PostgresAttachedHandleFactory {

    private final CommandRunner commandRunner;
    private final Duration shutdownTimeout;
    private final PostgresHandleOperationProviders operationProviders;

    /**
     * Creates a PostgresAttachedHandleFactory instance.
     *
     * @param commandRunner command runner value
     * @param shutdownTimeout shutdown timeout value
     * @param operationProviders operation providers value
     */
    public PostgresAttachedHandleFactory(
            final CommandRunner commandRunner,
            final Duration shutdownTimeout,
            final PostgresHandleOperationProviders operationProviders) {
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        this.operationProviders = Objects.requireNonNull(operationProviders, "operationProviders");
    }

    /**
     * Returns the attached handle result.
     *
     * @param metadata metadata value
     * @param configuration configuration value
     * @param layout layout value
     * @param runtimeDirectory runtime directory value
     * @return attached handle result
     */
    public RunningPostgres attachedHandle(
            final PostgresInstanceMetadata metadata,
            final StartPostgresWorkflow.Configuration configuration,
            final PostgresLayout layout,
            final Path runtimeDirectory) {
        final PostgresConnectionInfo attachedConnectionInfo = connectionInfo(metadata, configuration);

        return new AttachedPostgresHandle(
                attachedConnectionInfo,
                configuration.stopPolicy(),
                () -> stopAttached(runtimeDirectory, layout),
                operationProviders
                        .backup()
                        .create(new PostgresBackupOperationContext(
                                attachedConnectionInfo, metadata, layout, runtimeDirectory)),
                operationProviders
                        .restore()
                        .create(new PostgresBackupOperationContext(
                                attachedConnectionInfo, metadata, layout, runtimeDirectory)));
    }

    private static PostgresConnectionInfo connectionInfo(
            final PostgresInstanceMetadata metadata, final StartPostgresWorkflow.Configuration configuration) {
        return PostgresApplicationConnection.fromMetadata(metadata, configuration);
    }

    private void stopAttached(final Path runtimeDirectory, final PostgresLayout layout) {
        final CommandResult result;
        try {
            result = new PgCtlController(commandRunner, runtimeDirectory).stop(layout.dataDirectory(), shutdownTimeout);
        } catch (final ManagedPostgresException exception) {
            throw new PostgresShutdownException(
                    "PostgreSQL pg_ctl stop command failed", exception, exception.diagnosticReport());
        }
        if (!result.successful()) {
            throw new PostgresShutdownException(
                    "PostgreSQL pg_ctl stop failed",
                    PostgresStartupDiagnostics.commandDiagnostic("pg_ctl-stop", result));
        }
    }
}
