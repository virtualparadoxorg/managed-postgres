package eu.virtualparadox.managedpostgres.lifecycle.backup.operation;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.nio.file.Path;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;

/**
 * Context required to create a PostgreSQL backup operation for a running instance.
 *
 * @param connectionInfo connection details used by the backup command
 * @param metadata persisted instance metadata
 * @param layout PostgreSQL filesystem layout
 * @param runtimeDirectory PostgreSQL runtime directory
 */
public record PostgresBackupOperationContext(
        PostgresConnectionInfo connectionInfo,
        PostgresInstanceMetadata metadata,
        PostgresLayout layout,
        Path runtimeDirectory) {

    /**
     * Defines the value value.
     */
    public PostgresBackupOperationContext {
        Objects.requireNonNull(connectionInfo, "connectionInfo");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(runtimeDirectory, "runtimeDirectory");
    }
}
