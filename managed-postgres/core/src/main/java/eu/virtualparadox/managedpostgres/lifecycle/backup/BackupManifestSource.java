package eu.virtualparadox.managedpostgres.lifecycle.backup;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.time.Clock;
import java.util.Objects;

/**
 * Source data for rendering logical backup manifests.
 *
 * @param connectionInfo PostgreSQL connection details
 * @param metadata persisted PostgreSQL instance metadata
 * @param clock backup timestamp clock
 * @param frameworkVersion managed-postgres framework version
 */
public record BackupManifestSource(
        PostgresConnectionInfo connectionInfo,
        PostgresInstanceMetadata metadata,
        Clock clock,
        String frameworkVersion) {

    /**
     * Defines the value value.
     */
    public BackupManifestSource {
        Objects.requireNonNull(connectionInfo, "connectionInfo");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(frameworkVersion, "frameworkVersion");
    }
}
