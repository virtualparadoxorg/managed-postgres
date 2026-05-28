package eu.virtualparadox.managedpostgres.lifecycle.doctor.metadata;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import eu.virtualparadox.managedpostgres.lifecycle.doctor.DoctorReportFactory;

/**
 * Doctor metadata read result.
 *
 * @param metadata readable metadata
 * @param section metadata diagnostic section
 * @param additionalSections extra diagnostics from metadata read failures
 * @param failed whether metadata reading failed
 */
public record DoctorMetadataSnapshot(
        Optional<PostgresInstanceMetadata> metadata,
        DiagnosticSection section,
        List<DiagnosticSection> additionalSections,
        boolean failed) {

    /**
     * Defines the value value.
     */
    public DoctorMetadataSnapshot {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(additionalSections, "additionalSections");
        additionalSections = List.copyOf(additionalSections);
    }

    /**
     * Returns the absent result.
     *
     * @return absent result
     */
    public static DoctorMetadataSnapshot absent() {
        return new DoctorMetadataSnapshot(
                Optional.empty(),
                DoctorReportFactory.metadataAbsent(),
                List.of(),
                false);
    }

    /**
     * Returns the present result.
     *
     * @param metadata metadata value
     * @return present result
     */
    public static DoctorMetadataSnapshot present(final PostgresInstanceMetadata metadata) {
        return new DoctorMetadataSnapshot(
                Optional.of(Objects.requireNonNull(metadata, "metadata")),
                DoctorReportFactory.metadataPresent(metadata),
                List.of(),
                false);
    }

    /**
     * Returns the unreadable result.
     *
     * @param exception exception value
     * @return unreadable result
     */
    public static DoctorMetadataSnapshot unreadable(final ManagedPostgresException exception) {
        final ManagedPostgresException checkedException =
                Objects.requireNonNull(exception, "exception");
        final DiagnosticReport diagnosticReport = checkedException.diagnosticReport();
        final String message = Optional.ofNullable(checkedException.getMessage())
                .orElse(checkedException.getClass().getName());

        return new DoctorMetadataSnapshot(
                Optional.empty(),
                DoctorReportFactory.metadataUnreadable(message),
                diagnosticReport.sections(),
                true);
    }
}
