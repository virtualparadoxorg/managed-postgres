package eu.virtualparadox.managedpostgres.lifecycle.doctor;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.lifecycle.attach.AttachValidation;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.metadata.DoctorMetadataProbe;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.metadata.DoctorMetadataSnapshot;
import java.util.Objects;

/**
 * Coordinates high-level doctor probe status decisions.
 */
public final class DoctorProbeCoordinator {

    private final DoctorMetadataProbe metadataProbe;
    private final DoctorProbeSections sections;

    /**
     * Creates a DoctorProbeCoordinator instance.
     *
     * @param validation validation value
     */
    public DoctorProbeCoordinator(final AttachValidation validation) {
        sections = new DoctorProbeSections();
        metadataProbe = new DoctorMetadataProbe(validation, sections);
    }

    /**
     * Returns the inspect result.
     *
     * @param request request value
     * @return inspect result
     */
    public DoctorProbeSnapshot inspect(final DoctorProbeRequest request) {
        final DoctorProbeRequest checkedRequest = Objects.requireNonNull(request, "request");
        final DoctorMetadataSnapshot metadataSnapshot = checkedRequest.metadataSnapshot();
        final DoctorProbeSnapshot snapshot;
        if (metadataSnapshot.failed()) {
            snapshot = sections.skipped(PostgresStatus.FAILED, "metadata is unreadable");
        } else if (metadataSnapshot.metadata().isEmpty()) {
            snapshot = sections.skipped(PostgresStatus.STOPPED, "metadata is absent");
        } else {
            snapshot = metadataProbe.inspect(checkedRequest);
        }

        return snapshot;
    }
}
