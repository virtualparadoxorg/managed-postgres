package eu.virtualparadox.managedpostgres.lifecycle.doctor.metadata;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import eu.virtualparadox.managedpostgres.metadata.MetadataStore;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads managed PostgreSQL metadata for doctor diagnostics.
 */
public final class DoctorMetadataReader {

    private final ManagedFileSystem fileSystem;

    /**
     * Creates a DoctorMetadataReader instance.
     *
     * @param fileSystem file system value
     */
    public DoctorMetadataReader(final ManagedFileSystem fileSystem) {
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
    }

    /**
     * Returns the read result.
     *
     * @param metadataPath metadata path value
     * @return read result
     */
    public DoctorMetadataSnapshot read(final Optional<Path> metadataPath) {
        final Optional<Path> checkedPath = Objects.requireNonNull(metadataPath, "metadataPath");
        final DoctorMetadataSnapshot snapshot;
        if (checkedPath.isEmpty()) {
            snapshot = DoctorMetadataSnapshot.absent();
        } else {
            snapshot = readPersistentMetadata(checkedPath.orElseThrow());
        }

        return snapshot;
    }

    private DoctorMetadataSnapshot readPersistentMetadata(final Path metadataPath) {
        DoctorMetadataSnapshot snapshot;
        try {
            final Optional<PostgresInstanceMetadata> metadata =
                    new MetadataStore(metadataPath, fileSystem).read();
            snapshot = metadata
                    .map(DoctorMetadataSnapshot::present)
                    .orElseGet(DoctorMetadataSnapshot::absent);
        } catch (final ManagedPostgresException exception) {
            snapshot = DoctorMetadataSnapshot.unreadable(exception);
        }

        return snapshot;
    }
}
