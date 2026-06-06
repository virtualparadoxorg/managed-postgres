package eu.virtualparadox.managedpostgres.lifecycle.doctor.live;

import eu.virtualparadox.managedpostgres.lifecycle.attach.AttachValidation;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorProbeSections;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorProbeSnapshot;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorProbeValues;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.process.PostgresProcessProbe;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.util.Objects;

/**
 * Probes process and port evidence for compatible metadata.
 */
public final class DoctorLiveProbe {

    private final AttachValidation validation;
    private final DoctorJdbcProbe jdbcProbe;
    private final PostgresProcessProbe processProbe;
    private final DoctorProbeSections sections;

    /**
     * Creates a DoctorLiveProbe instance.
     *
     * @param validation validation value
     * @param sections sections value
     */
    public DoctorLiveProbe(final AttachValidation validation, final DoctorProbeSections sections) {
        this.validation = Objects.requireNonNull(validation, "validation");
        this.sections = Objects.requireNonNull(sections, "sections");
        processProbe = new PostgresProcessProbe(this.validation.processLookup());
        jdbcProbe = new DoctorJdbcProbe(this.validation, this.sections);
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
        final PostgresProcessProbe.ProcessProbeResult processResult = processProbe.probe(metadata);
        values.put("process", processResult.status());
        final DoctorProbeSnapshot snapshot;
        if (!processResult.accepted()) {
            snapshot = sections.unhealthy(values, processResult.summary());
        } else if (!validation.portProbe().test(metadata)) {
            values.put("port", "closed");
            snapshot = sections.unhealthy(values, "Port is not accepting PostgreSQL connections");
        } else {
            values.put("port", "open");
            snapshot = jdbcProbe.inspect(configuration, layout, metadata, values);
        }

        return snapshot;
    }
}
