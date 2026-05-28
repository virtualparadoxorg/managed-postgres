package eu.virtualparadox.managedpostgres.lifecycle.stop;

import eu.virtualparadox.managedpostgres.exception.PostgresShutdownException;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.lifecycle.attach.PostgresAttachCompatibility;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;

/**
 * Verifies that persisted metadata belongs to the requested stop configuration.
 */
public final class PostgresStopCompatibility {

    /**
     * Creates a PostgresStopCompatibility instance.
     */
    public PostgresStopCompatibility() {
    }

    /**
     * Performs the verify operation.
     *
     * @param configuration configuration value
     * @param layout layout value
     * @param metadata metadata value
     */
    public void verify(
            final StartPostgresWorkflow.Configuration configuration,
            final PostgresLayout layout,
            final PostgresInstanceMetadata metadata) {
        new PostgresAttachCompatibility().mismatch(configuration, layout, metadata)
                .ifPresent(reason -> {
                    throw new PostgresShutdownException(
                            "PostgreSQL metadata does not match stop configuration",
                            PostgresStopDiagnostics.mismatch(layout, reason));
                });
    }
}
