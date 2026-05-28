package eu.virtualparadox.managedpostgres.lifecycle.attach;

import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;

/**
 * Request passed to an attach JDBC probe.
 *
 * @param metadata persisted instance metadata
 * @param configuration requested startup configuration
 * @param layout expected PostgreSQL layout
 */
public record AttachJdbcProbeRequest(
        PostgresInstanceMetadata metadata,
        StartPostgresWorkflow.Configuration configuration,
        PostgresLayout layout) {

    /**
     * Defines the value value.
     */
    public AttachJdbcProbeRequest {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(layout, "layout");
    }
}
