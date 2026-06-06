package eu.virtualparadox.managedpostgres.runtime.packaging;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable metadata for a published runtime bundle.
 *
 * @param postgresVersion PostgreSQL version contained in the bundle
 * @param bundleRevision managed-postgres bundle revision identifier
 * @param targetPlatform target platform identifier
 * @param archiveFileName published archive file name
 * @param sha256 checksum for the archive contents
 * @param publishedAt publication timestamp
 * @param sourceUri canonical source archive or release reference
 */
public record BundleManifest(
        String postgresVersion,
        String bundleRevision,
        TargetPlatform targetPlatform,
        String archiveFileName,
        String sha256,
        Instant publishedAt,
        String sourceUri) {

    /**
     * Creates immutable bundle metadata.
     *
     * @param postgresVersion PostgreSQL version contained in the bundle
     * @param bundleRevision managed-postgres bundle revision identifier
     * @param targetPlatform target platform identifier
     * @param archiveFileName published archive file name
     * @param sha256 SHA-256 checksum in lower-case hexadecimal format
     * @param publishedAt publication timestamp
     * @param sourceUri canonical source archive or release reference
     */
    public BundleManifest {
        final TargetPlatform validatedTargetPlatform = Objects.requireNonNull(targetPlatform, "targetPlatform");
        final Instant validatedPublishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
        postgresVersion = requireNonBlank(postgresVersion, "postgresVersion");
        bundleRevision = requireNonBlank(bundleRevision, "bundleRevision");
        targetPlatform = validatedTargetPlatform;
        archiveFileName = requireNonBlank(archiveFileName, "archiveFileName");
        sha256 = requireNonBlank(sha256, "sha256");
        publishedAt = validatedPublishedAt;
        sourceUri = requireNonBlank(sourceUri, "sourceUri");
    }

    private static String requireNonBlank(final String value, final String fieldName) {
        final String checkedValue = Objects.requireNonNull(value, fieldName);
        if (checkedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return checkedValue;
    }
}
