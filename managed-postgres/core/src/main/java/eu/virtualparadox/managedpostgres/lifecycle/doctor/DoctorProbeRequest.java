package eu.virtualparadox.managedpostgres.lifecycle.doctor;

import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import java.util.Objects;
import java.util.Optional;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.layout.DoctorLayoutPlan;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.metadata.DoctorMetadataSnapshot;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;

/**
 * Internal doctor probe request.
 *
 * @param startConfiguration internal startup-compatible configuration
 * @param layout persistent layout when available without side effects
 * @param metadataSnapshot metadata read result
 */
public record DoctorProbeRequest(
        StartPostgresWorkflow.Configuration startConfiguration,
        Optional<PostgresLayout> layout,
        DoctorMetadataSnapshot metadataSnapshot) {

    /**
     * Defines the value value.
     */
    public DoctorProbeRequest {
        Objects.requireNonNull(startConfiguration, "startConfiguration");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(metadataSnapshot, "metadataSnapshot");
    }

    /**
     * Returns the from result.
     *
     * @param configuration configuration value
     * @param layoutPlan layout plan value
     * @param metadataSnapshot metadata snapshot value
     * @return from result
     */
    public static DoctorProbeRequest from(
            final ManagedPostgresConfiguration configuration,
            final DoctorLayoutPlan layoutPlan,
            final DoctorMetadataSnapshot metadataSnapshot) {
        return new DoctorProbeRequest(
                new StartPostgresWorkflow.Configuration(configuration),
                Objects.requireNonNull(layoutPlan, "layoutPlan").layout(),
                metadataSnapshot);
    }
}
