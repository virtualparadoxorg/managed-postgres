package eu.virtualparadox.managedpostgres.lifecycle.status;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolves PostgreSQL status from internal metadata.
 */
public final class PostgresStatusService {

    private final MetadataReader metadataReader;
    private final MetadataStatusProbe metadataStatusProbe;
    private Optional<DiagnosticReport> diagnosticReport;

    /**
     * Creates a PostgreSQL status service.
     *
     * @param metadataReader metadata reader
     */
    public PostgresStatusService(final MetadataReader metadataReader) {
        this(metadataReader, PostgresStatusService::unvalidatedMetadata);
    }

    /**
     * Creates a PostgreSQL status service with metadata validation.
     *
     * @param metadataReader metadata reader
     * @param metadataStatusProbe metadata status probe
     */
    public PostgresStatusService(final MetadataReader metadataReader, final MetadataStatusProbe metadataStatusProbe) {
        this.metadataReader = Objects.requireNonNull(metadataReader, "metadataReader");
        this.metadataStatusProbe = Objects.requireNonNull(metadataStatusProbe, "metadataStatusProbe");
        this.diagnosticReport = Optional.empty();
    }

    /**
     * Returns PostgreSQL status without exposing raw metadata I/O failures.
     *
     * @return PostgreSQL status
     */
    public PostgresStatus status() {
        PostgresStatus status;
        try {
            status = statusFromMetadata(metadataReader.read());
        } catch (IOException exception) {
            diagnosticReport = Optional.of(metadataDiagnostic(exception));
            status = PostgresStatus.FAILED;
        }

        return status;
    }

    /**
     * Returns the last diagnostic report, when status resolution failed.
     *
     * @return diagnostic report
     */
    public Optional<DiagnosticReport> diagnosticReport() {
        return diagnosticReport;
    }

    private PostgresStatus statusFromMetadata(final Optional<PostgresInstanceMetadata> metadata) {
        final PostgresStatus status;
        if (metadata.isPresent()) {
            final PostgresStatusSnapshot snapshot = metadataStatusProbe.probe(metadata.orElseThrow());
            diagnosticReport = snapshot.diagnosticReport();
            status = snapshot.status();
        } else {
            diagnosticReport = Optional.empty();
            status = PostgresStatus.STOPPED;
        }

        return status;
    }

    private static DiagnosticReport metadataDiagnostic(final IOException exception) {
        return new DiagnosticReport(List.of(
                new DiagnosticSection("metadata", Map.of("status", "stale", "message", diagnosticMessage(exception)))));
    }

    private static String diagnosticMessage(final IOException exception) {
        return StringUtils.defaultIfBlank(
                exception.getMessage(), exception.getClass().getName());
    }

    private static PostgresStatusSnapshot unvalidatedMetadata(final PostgresInstanceMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        return PostgresStatusSnapshot.failed(new DiagnosticReport(List.of(new DiagnosticSection(
                "metadata",
                Map.of(
                        "status", "unvalidated",
                        "message", "Metadata exists but no runtime status probe is configured")))));
    }

    /**
     * Reads PostgreSQL metadata.
     */
    @FunctionalInterface
    interface MetadataReader {

        /**
         * Reads metadata if it exists.
         *
         * @return metadata
         * @throws IOException when the metadata cannot be read
         */
        public Optional<PostgresInstanceMetadata> read() throws IOException;
    }

    /**
     * Probes runtime status for metadata that exists.
     */
    @FunctionalInterface
    interface MetadataStatusProbe {

        /**
         * Probes metadata-backed PostgreSQL status.
         *
         * @param metadata instance metadata
         * @return status snapshot
         */
        public PostgresStatusSnapshot probe(PostgresInstanceMetadata metadata);
    }
}
