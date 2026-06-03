package eu.virtualparadox.managedpostgres.lifecycle.doctor.layout;

import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Non-mutating doctor view of the managed PostgreSQL filesystem layout.
 *
 * @param metadataPath metadata path when it can be planned without side effects
 * @param layout persistent layout when it can be planned without side effects
 * @param section diagnostic layout section
 * @param credentialSection credential store diagnostic section
 */
public record DoctorLayoutPlan(
        Optional<Path> metadataPath,
        Optional<PostgresLayout> layout,
        DiagnosticSection section,
        DiagnosticSection credentialSection) {

    /**
     * Defines the value value.
     */
    public DoctorLayoutPlan {
        Objects.requireNonNull(metadataPath, "metadataPath");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(credentialSection, "credentialSection");
    }
}
