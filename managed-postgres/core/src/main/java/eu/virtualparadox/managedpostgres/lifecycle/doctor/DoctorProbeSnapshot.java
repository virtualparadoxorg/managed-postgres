package eu.virtualparadox.managedpostgres.lifecycle.doctor;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.util.List;
import java.util.Objects;

/**
 * Doctor probe result with status and diagnostic sections.
 *
 * @param status status implied by probe evidence
 * @param section primary probe section
 * @param additionalSections extra probe diagnostics
 */
public record DoctorProbeSnapshot(
        PostgresStatus status, DiagnosticSection section, List<DiagnosticSection> additionalSections) {

    /**
     * Defines the value value.
     */
    public DoctorProbeSnapshot {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(additionalSections, "additionalSections");
        additionalSections = List.copyOf(additionalSections);
    }

    /**
     * Returns the of result.
     *
     * @param status status value
     * @param section section value
     * @return of result
     */
    public static DoctorProbeSnapshot of(final PostgresStatus status, final DiagnosticSection section) {
        return new DoctorProbeSnapshot(status, section, List.of());
    }
}
