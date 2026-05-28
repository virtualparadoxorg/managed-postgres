package eu.virtualparadox.managedpostgres.metadata;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Persisted metadata for a managed PostgreSQL instance.
 *
 * @param schemaVersion metadata schema version
 * @param instanceId instance identifier
 * @param clusterId PostgreSQL cluster identifier
 * @param name configured instance name
 * @param dataDirectory PostgreSQL data directory
 * @param host connection host
 * @param port connection port
 * @param database default database
 * @param owner database owner
 * @param postgresqlVersion full PostgreSQL version
 * @param postgresqlMajor PostgreSQL major version
 * @param attachmentMode attachment mode used for the instance
 * @param pid PostgreSQL process id, or zero when no process is attached
 * @param configHash drift hash for rendered configuration
 * @param createdAt metadata creation timestamp
 * @param updatedAt metadata update timestamp
 */
public record PostgresInstanceMetadata(
        int schemaVersion,
        String instanceId,
        String clusterId,
        String name,
        Path dataDirectory,
        String host,
        int port,
        String database,
        String owner,
        String postgresqlVersion,
        int postgresqlMajor,
        String attachmentMode,
        long pid,
        String configHash,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Creates instance metadata.
     *
     * @param schemaVersion metadata schema version
     * @param instanceId instance identifier
     * @param clusterId PostgreSQL cluster identifier
     * @param name configured instance name
     * @param dataDirectory PostgreSQL data directory
     * @param host connection host
     * @param port connection port
     * @param database default database
     * @param owner database owner
     * @param postgresqlVersion full PostgreSQL version
     * @param postgresqlMajor PostgreSQL major version
     * @param attachmentMode attachment mode used for the instance
     * @param pid PostgreSQL process id, or zero when no process is attached
     * @param configHash drift hash for rendered configuration
     * @param createdAt metadata creation timestamp
     * @param updatedAt metadata update timestamp
     */
    public PostgresInstanceMetadata {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireNotBlank(instanceId, "instanceId");
        requireNotBlank(clusterId, "clusterId");
        requireNotBlank(name, "name");
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        requireNotBlank(host, "host");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        requireNotBlank(database, "database");
        requireNotBlank(owner, "owner");
        requireNotBlank(postgresqlVersion, "postgresqlVersion");
        if (postgresqlMajor < 1) {
            throw new IllegalArgumentException("postgresqlMajor must be positive");
        }
        requireNotBlank(attachmentMode, "attachmentMode");
        if (pid < 0) {
            throw new IllegalArgumentException("pid must not be negative");
        }
        requireNotBlank(configHash, "configHash");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static void requireNotBlank(final String value, final String name) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("%s must not be blank".formatted(name));
        }
    }
}
