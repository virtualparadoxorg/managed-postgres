package eu.virtualparadox.managedpostgres.lifecycle.doctor;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.diagnostics.CommandRedactor;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.util.Objects;

/**
 * Creates redacted probe diagnostic sections.
 */
public final class DoctorProbeSections {

    /**
     * Creates a DoctorProbeSections instance.
     */
    public DoctorProbeSections() {
    }

    /**
     * Returns the skipped result.
     *
     * @param status status value
     * @param summary summary value
     * @return skipped result
     */
    public DoctorProbeSnapshot skipped(final PostgresStatus status, final String summary) {
        final DoctorProbeValues values = DoctorProbeValues.skipped();
        values.put("status", "skipped");
        values.put("summary", redact(summary));

        return DoctorProbeSnapshot.of(status, new DiagnosticSection("probes", values.map()));
    }

    /**
     * Returns the unhealthy result.
     *
     * @param values values value
     * @param summary summary value
     * @return unhealthy result
     */
    public DoctorProbeSnapshot unhealthy(final DoctorProbeValues values, final String summary) {
        return DoctorProbeSnapshot.of(PostgresStatus.FAILED, section(values, summary, "unhealthy"));
    }

    /**
     * Returns the section result.
     *
     * @param values values value
     * @param summary summary value
     * @param status status value
     * @return section result
     */
    public DiagnosticSection section(
            final DoctorProbeValues values,
            final String summary,
            final String status) {
        values.put("status", status);
        values.put("summary", redact(summary));

        return new DiagnosticSection("probes", values.map());
    }

    /**
     * Returns the redact result.
     *
     * @param value value value
     * @return redact result
     */
    public static String redact(final String value) {
        return CommandRedactor.redact(Objects.toString(value, "probe did not provide a summary"));
    }
}
