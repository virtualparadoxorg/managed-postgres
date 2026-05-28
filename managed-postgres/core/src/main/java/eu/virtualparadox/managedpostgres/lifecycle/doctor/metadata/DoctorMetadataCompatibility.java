package eu.virtualparadox.managedpostgres.lifecycle.doctor.metadata;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.util.Objects;
import java.util.Optional;
import eu.virtualparadox.managedpostgres.lifecycle.attach.PostgresAttachCompatibility;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorProbeRequest;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorProbeSections;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorProbeSnapshot;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorProbeValues;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;

/**
 * Builds doctor snapshots for metadata compatibility failures.
 */
final class DoctorMetadataCompatibility {

    private final PostgresAttachCompatibility compatibility;
    private final DoctorProbeSections sections;

    DoctorMetadataCompatibility(final DoctorProbeSections sections) {
        this.sections = Objects.requireNonNull(sections, "sections");
        compatibility = new PostgresAttachCompatibility();
    }

    Optional<DoctorProbeSnapshot> incompatibleSnapshot(
            final DoctorProbeRequest request,
            final PostgresLayout layout,
            final PostgresInstanceMetadata metadata,
            final DoctorProbeValues values) {
        final Optional<String> mismatch = compatibility.mismatch(
                request.startConfiguration(),
                layout,
                metadata);
        final Optional<DoctorProbeSnapshot> snapshot;
        if (mismatch.isEmpty()) {
            snapshot = Optional.empty();
        } else {
            values.put("compatibility", "incompatible");
            snapshot = Optional.of(new DoctorProbeSnapshot(
                    PostgresStatus.FAILED,
                    sections.section(
                            values,
                            "PostgreSQL metadata is incompatible: " + mismatch.orElseThrow(),
                            "unhealthy"),
                    compatibility.diagnosticReport(
                            request.startConfiguration(),
                            layout,
                            metadata).sections()));
        }

        return snapshot;
    }
}
