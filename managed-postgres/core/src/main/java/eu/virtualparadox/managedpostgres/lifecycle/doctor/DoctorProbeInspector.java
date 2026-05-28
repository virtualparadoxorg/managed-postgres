package eu.virtualparadox.managedpostgres.lifecycle.doctor;

import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.attach.AttachValidation;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.layout.DoctorLayoutPlan;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.metadata.DoctorMetadataSnapshot;

/**
 * Inspects metadata-backed PostgreSQL health without attaching to or mutating the process.
 */
public final class DoctorProbeInspector {

    private final DoctorProbeCoordinator coordinator;

    /**
     * Creates a DoctorProbeInspector instance.
     */
    public DoctorProbeInspector() {
        this(AttachValidation.systemDefault());
    }

    /**
     * Creates a DoctorProbeInspector instance.
     *
     * @param validation validation value
     */
    public DoctorProbeInspector(final AttachValidation validation) {
        coordinator = new DoctorProbeCoordinator(validation);
    }

    /**
     * Returns the inspect result.
     *
     * @param configuration configuration value
     * @param layoutPlan layout plan value
     * @param metadataSnapshot metadata snapshot value
     * @return inspect result
     */
    public DoctorProbeSnapshot inspect(
            final ManagedPostgresConfiguration configuration,
            final DoctorLayoutPlan layoutPlan,
            final DoctorMetadataSnapshot metadataSnapshot) {
        return coordinator.inspect(DoctorProbeRequest.from(
                Objects.requireNonNull(configuration, "configuration"),
                Objects.requireNonNull(layoutPlan, "layoutPlan"),
                Objects.requireNonNull(metadataSnapshot, "metadataSnapshot")));
    }

    /**
     * Returns the inspect result.
     *
     * @param request request value
     * @return inspect result
     */
    public DoctorProbeSnapshot inspect(final DoctorProbeRequest request) {
        return coordinator.inspect(request);
    }
}
