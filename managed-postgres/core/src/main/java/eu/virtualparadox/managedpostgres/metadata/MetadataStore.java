package eu.virtualparadox.managedpostgres.metadata;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperation;
import eu.virtualparadox.managedpostgres.filesystem.ManagedFileSystem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.apache.commons.lang3.StringUtils;

/**
 * Persists PostgreSQL instance metadata.
 */
public final class MetadataStore {

    private final Path path;
    private final ManagedFileSystem fileSystem;

    /**
     * Creates a metadata store backed by a managed file system.
     *
     * @param path metadata file path
     * @param fileSystem managed file system adapter
     */
    public MetadataStore(final Path path, final ManagedFileSystem fileSystem) {
        this.path = Objects.requireNonNull(path, "path");
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
    }

    /**
     * Writes metadata atomically.
     *
     * @param metadata metadata to write
     */
    public void write(final PostgresInstanceMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");

        writeJsonAtomically("write-metadata", serialize(metadata));
    }

    /**
     * Writes stale metadata diagnostics next to the active metadata file.
     *
     * @param metadata stale metadata
     * @param reason reason the metadata was marked stale
     */
    public void markStale(final PostgresInstanceMetadata metadata, final String reason) {
        Objects.requireNonNull(metadata, "metadata");
        final Path stalePath = path.resolveSibling("metadata.stale.json");

        writeJsonAtomically(stalePath, "mark-stale-metadata", MetadataJsonCodec.serializeStale(metadata, reason));
    }

    /**
     * Writes an early stable port reservation atomically.
     *
     * @param key stable port key
     * @param port selected PostgreSQL port
     */
    public void writePortReservation(final String key, final int port) {
        final String checkedKey = requireNotBlank(key, "key");
        MetadataJsonCodec.validatePort(port);

        writeJsonAtomically("write-port-reservation", serializePortReservation(checkedKey, port));
    }

    private void writeJsonAtomically(final String operationName, final String content) {
        writeJsonAtomically(path, operationName, content);
    }

    private void writeJsonAtomically(final Path target, final String operationName, final String content) {
        try {
            try (FileSystemOperation operation = fileSystem.beginOperation(
                    requireNotBlank(operationName, "operationName"),
                    operationRoot(target))) {
                operation.writeUtf8Atomically(target, content);
                operation.commit();
            }
        } catch (UncheckedIOException exception) {
            throw metadataFailure("Failed to write PostgreSQL metadata", exception);
        }
    }

    /**
     * Reads the selected PostgreSQL port from metadata when metadata exists.
     *
     * @return metadata port, or empty when metadata does not exist
     */
    public OptionalInt readPort() {
        final OptionalInt port;
        if (!Files.exists(path)) {
            port = OptionalInt.empty();
        } else {
            try {
                port = OptionalInt.of(MetadataJsonCodec.parsePort(path, Files.readString(path)));
            } catch (IOException exception) {
                throw metadataFailure("Failed to read PostgreSQL metadata", exception);
            }
        }

        return port;
    }

    /**
     * Reads PostgreSQL instance metadata when present.
     *
     * @return instance metadata, or empty when metadata does not exist
     */
    public Optional<PostgresInstanceMetadata> read() {
        final Optional<PostgresInstanceMetadata> metadata;
        if (!Files.exists(path)) {
            metadata = Optional.empty();
        } else {
            try {
                final String metadataContent = Files.readString(path);
                if (MetadataJsonCodec.isPortReservation(metadataContent)) {
                    metadata = Optional.empty();
                } else {
                    metadata = Optional.of(MetadataJsonCodec.parse(path, metadataContent));
                }
            } catch (IOException exception) {
                throw metadataFailure("Failed to read PostgreSQL metadata", exception);
            }
        }

        return metadata;
    }

    /**
     * Deletes active PostgreSQL instance metadata when present.
     */
    public void delete() {
        try {
            fileSystem.deleteIfExists(path);
        } catch (UncheckedIOException exception) {
            throw metadataFailure("Failed to delete PostgreSQL metadata", exception);
        }
    }

    /**
     * Serializes metadata as stable JSON.
     *
     * @param metadata metadata to serialize
     * @return serialized metadata
     */
    public String serialize(final PostgresInstanceMetadata metadata) {
        return MetadataJsonCodec.serialize(metadata);
    }

    /**
     * Serializes a stable port reservation as stable JSON.
     *
     * @param key stable port key
     * @param port selected PostgreSQL port
     * @return serialized port reservation metadata
     */
    public String serializePortReservation(final String key, final int port) {
        return MetadataJsonCodec.serializePortReservation(key, port);
    }

    private static Path operationRoot(final Path target) {
        final Path normalizedTarget = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        final Path parent = normalizedTarget.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("target must have a parent directory");
        }

        return parent;
    }

    private ManagedPostgresException metadataFailure(final String message, final Throwable cause) {
        return new ManagedPostgresException(message, cause, MetadataJsonCodec.diagnostic(path));
    }

    private static String requireNotBlank(final String value, final String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value;
    }
}
