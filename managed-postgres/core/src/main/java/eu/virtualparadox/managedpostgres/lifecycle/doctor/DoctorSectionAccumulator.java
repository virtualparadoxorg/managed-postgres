package eu.virtualparadox.managedpostgres.lifecycle.doctor;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Accumulates doctor sections before creating the immutable public report.
 */
public final class DoctorSectionAccumulator {

    private final List<DiagnosticSection> sections;

    /**
     * Creates a DoctorSectionAccumulator instance.
     */
    public DoctorSectionAccumulator() {
        sections = new ArrayList<>();
    }

    /**
     * Performs the add operation.
     *
     * @param section section value
     */
    public void add(final DiagnosticSection section) {
        sections.add(Objects.requireNonNull(section, "section"));
    }

    /**
     * Performs the add all operation.
     *
     * @param additionalSections additional sections value
     */
    public void addAll(final Collection<DiagnosticSection> additionalSections) {
        sections.addAll(Objects.requireNonNull(additionalSections, "additionalSections"));
    }

    /**
     * Returns the report result.
     *
     * @param status status value
     * @return report result
     */
    public DoctorReport report(final PostgresStatus status) {
        return new DoctorReport(Objects.requireNonNull(status, "status"), sections);
    }
}
