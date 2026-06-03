package eu.virtualparadox.managedpostgres.lifecycle.doctor.live;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.lifecycle.attach.AttachJdbcProbeRequest;
import eu.virtualparadox.managedpostgres.lifecycle.attach.AttachValidation;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorProbeSections;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorProbeSnapshot;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorProbeValues;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.probe.PostgresProbeResult;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.util.Objects;

/**
 * Probes JDBC identity after process and port evidence are acceptable.
 */
public final class DoctorJdbcProbe {

    private final AttachValidation validation;
    private final DoctorProbeSections sections;

    /**
     * Creates a DoctorJdbcProbe instance.
     *
     * @param validation validation value
     * @param sections sections value
     */
    public DoctorJdbcProbe(final AttachValidation validation, final DoctorProbeSections sections) {
        this.validation = Objects.requireNonNull(validation, "validation");
        this.sections = Objects.requireNonNull(sections, "sections");
    }

    /**
     * Returns the inspect result.
     *
     * @param configuration configuration value
     * @param layout layout value
     * @param metadata metadata value
     * @param values values value
     * @return inspect result
     */
    public DoctorProbeSnapshot inspect(
            final StartPostgresWorkflow.Configuration configuration,
            final PostgresLayout layout,
            final PostgresInstanceMetadata metadata,
            final DoctorProbeValues values) {
        final PostgresProbeResult probeResult =
                validation.jdbcProbe().apply(new AttachJdbcProbeRequest(metadata, configuration, layout));
        final DoctorProbeSnapshot snapshot;
        if (probeResult.healthy()) {
            values.put("jdbc", "healthy");
            values.put("status", "healthy");
            values.put("summary", DoctorProbeSections.redact(probeResult.summary()));
            snapshot = DoctorProbeSnapshot.of(PostgresStatus.RUNNING, new DiagnosticSection("probes", values.map()));
        } else {
            values.put("jdbc", "unhealthy");
            snapshot = new DoctorProbeSnapshot(
                    PostgresStatus.FAILED,
                    sections.section(values, probeResult.summary(), "unhealthy"),
                    probeResult.diagnosticReport().sections());
        }

        return snapshot;
    }
}
