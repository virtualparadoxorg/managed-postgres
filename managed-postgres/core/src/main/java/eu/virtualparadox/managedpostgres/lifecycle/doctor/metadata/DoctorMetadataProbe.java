package eu.virtualparadox.managedpostgres.lifecycle.doctor.metadata;

import eu.virtualparadox.managedpostgres.lifecycle.attach.AttachValidation;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorProbeRequest;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorProbeSections;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorProbeSnapshot;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorProbeValues;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.live.DoctorLiveProbe;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.util.Objects;
import java.util.Optional;

/**
 * Probes readable metadata against expected configuration and layout.
 */
public final class DoctorMetadataProbe {

    private final DoctorLiveProbe liveProbe;
    private final DoctorMetadataCompatibility metadataCompatibility;
    private final DoctorProbeSections sections;

    /**
     * Creates a DoctorMetadataProbe instance.
     *
     * @param validation validation value
     * @param sections sections value
     */
    public DoctorMetadataProbe(final AttachValidation validation, final DoctorProbeSections sections) {
        this.sections = Objects.requireNonNull(sections, "sections");
        metadataCompatibility = new DoctorMetadataCompatibility(this.sections);
        liveProbe = new DoctorLiveProbe(validation, this.sections);
    }

    /**
     * Returns the inspect result.
     *
     * @param request request value
     * @return inspect result
     */
    public DoctorProbeSnapshot inspect(final DoctorProbeRequest request) {
        final DoctorProbeRequest checkedRequest = Objects.requireNonNull(request, "request");
        final DoctorProbeSnapshot snapshot;
        if (checkedRequest.layout().isEmpty()) {
            snapshot = sections.unhealthy(
                    DoctorProbeValues.skipped(), "layout is not available for metadata-backed probes");
        } else {
            snapshot = inspectWithLayout(checkedRequest, checkedRequest.layout().orElseThrow());
        }

        return snapshot;
    }

    private DoctorProbeSnapshot inspectWithLayout(final DoctorProbeRequest request, final PostgresLayout layout) {
        final PostgresInstanceMetadata metadata =
                request.metadataSnapshot().metadata().orElseThrow();
        final DoctorProbeValues values = DoctorProbeValues.skipped();
        final Optional<DoctorProbeSnapshot> incompatibleSnapshot =
                metadataCompatibility.incompatibleSnapshot(request, layout, metadata, values);
        final DoctorProbeSnapshot snapshot;
        if (incompatibleSnapshot.isPresent()) {
            snapshot = incompatibleSnapshot.orElseThrow();
        } else {
            values.put("compatibility", "compatible");
            snapshot = liveProbe.inspect(request.startConfiguration(), layout, metadata, values);
        }

        return snapshot;
    }
}
