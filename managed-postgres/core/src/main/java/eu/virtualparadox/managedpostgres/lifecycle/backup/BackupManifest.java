package eu.virtualparadox.managedpostgres.lifecycle.backup;

import java.time.Instant;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Stable metadata sidecar for a logical PostgreSQL backup.
 *
 * @param manifestVersion manifest schema version
 * @param createdAt backup creation timestamp
 * @param frameworkVersion managed-postgres framework version
 * @param postgresqlVersion PostgreSQL server version
 * @param postgresqlMajor PostgreSQL major version
 * @param clusterId managed cluster identifier
 * @param database dumped database
 * @param format logical backup format
 * @param checksumAlgorithm checksum algorithm
 * @param checksum backup checksum
 */
public record BackupManifest(
        int manifestVersion,
        Instant createdAt,
        String frameworkVersion,
        String postgresqlVersion,
        int postgresqlMajor,
        String clusterId,
        String database,
        BackupFormat format,
        String checksumAlgorithm,
        String checksum) {

    /**
     * Defines the value value.
     */
    public BackupManifest {
        if (manifestVersion < 1) {
            throw new IllegalArgumentException("manifestVersion must be positive");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        requireNotBlank(frameworkVersion, "frameworkVersion");
        requireNotBlank(postgresqlVersion, "postgresqlVersion");
        if (postgresqlMajor < 1) {
            throw new IllegalArgumentException("postgresqlMajor must be positive");
        }
        requireNotBlank(clusterId, "clusterId");
        requireNotBlank(database, "database");
        Objects.requireNonNull(format, "format");
        requireNotBlank(checksumAlgorithm, "checksumAlgorithm");
        requireNotBlank(checksum, "checksum");
    }

    private static void requireNotBlank(final String value, final String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
