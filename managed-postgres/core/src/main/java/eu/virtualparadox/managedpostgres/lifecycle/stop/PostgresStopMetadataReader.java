package eu.virtualparadox.managedpostgres.lifecycle.stop;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.exception.PostgresShutdownException;
import eu.virtualparadox.managedpostgres.metadata.MetadataStore;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads stop metadata and converts corrupt metadata into shutdown diagnostics.
 */
public final class PostgresStopMetadataReader {

    /**
     * Creates a PostgresStopMetadataReader instance.
     */
    public PostgresStopMetadataReader() {}

    /**
     * Returns the read result.
     *
     * @param metadataStore metadata store value
     * @return read result
     */
    public Optional<PostgresInstanceMetadata> read(final MetadataStore metadataStore) {
        try {
            return Objects.requireNonNull(metadataStore, "metadataStore").read();
        } catch (final ManagedPostgresException exception) {
            throw new PostgresShutdownException(
                    "PostgreSQL metadata could not be read before stop",
                    exception,
                    PostgresStopDiagnostics.wrappedFailure(
                            Objects.toString(
                                    exception.getMessage(), exception.getClass().getName()),
                            exception.diagnosticReport()));
        }
    }
}
