package eu.virtualparadox.managedpostgres.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Manifest describing a resolved PostgreSQL runtime.
 *
 * @param postgresqlVersion PostgreSQL version provided by the runtime
 * @param runtimeSource runtime source kind
 * @param runtimeIdentity downloaded runtime identity, when available
 */
public record PostgresRuntimeManifest(
        String postgresqlVersion, String runtimeSource, Optional<PostgresRuntimeIdentity> runtimeIdentity) {

    private static final String SYSTEM = "system";
    private static final String EXISTING = "existing";
    private static final String DOWNLOADED = "downloaded";
    private static final List<String> VALID_RUNTIME_SOURCES = List.of(SYSTEM, EXISTING, DOWNLOADED);

    /**
     * Creates a PostgreSQL runtime manifest.
     *
     * @param postgresqlVersion PostgreSQL version provided by the runtime
     * @param runtimeSource runtime source kind
     * @param runtimeIdentity downloaded runtime identity, when available
     */
    public PostgresRuntimeManifest {
        final Optional<PostgresRuntimeIdentity> validatedRuntimeIdentity =
                Objects.requireNonNull(runtimeIdentity, "runtimeIdentity");
        postgresqlVersion = requireNotBlank(postgresqlVersion, "postgresqlVersion");
        runtimeSource = requireRuntimeSource(runtimeSource);
        requireDownloadedChecksum(runtimeSource, validatedRuntimeIdentity);
        runtimeIdentity = validatedRuntimeIdentity;
    }

    /**
     * Creates a manifest for a system PostgreSQL runtime.
     *
     * @param postgresqlVersion PostgreSQL version provided by the runtime
     * @return system runtime manifest
     */
    public static PostgresRuntimeManifest system(final String postgresqlVersion) {
        return new PostgresRuntimeManifest(postgresqlVersion, SYSTEM, Optional.empty());
    }

    /**
     * Creates a manifest for an existing PostgreSQL runtime directory.
     *
     * @param postgresqlVersion PostgreSQL version provided by the runtime
     * @return existing runtime manifest
     */
    public static PostgresRuntimeManifest existing(final String postgresqlVersion) {
        return new PostgresRuntimeManifest(postgresqlVersion, EXISTING, Optional.empty());
    }

    /**
     * Creates a manifest for a downloaded PostgreSQL runtime artifact.
     *
     * @param postgresqlVersion PostgreSQL version provided by the runtime
     * @param runtimeIdentity downloaded runtime identity
     * @return downloaded runtime manifest
     */
    public static PostgresRuntimeManifest downloaded(
            final String postgresqlVersion, final PostgresRuntimeIdentity runtimeIdentity) {
        return downloaded(postgresqlVersion, Optional.of(runtimeIdentity));
    }

    /**
     * Creates a manifest for a downloaded PostgreSQL runtime artifact.
     *
     * @param postgresqlVersion PostgreSQL version provided by the runtime
     * @param runtimeIdentity downloaded runtime identity
     * @return downloaded runtime manifest
     */
    public static PostgresRuntimeManifest downloaded(
            final String postgresqlVersion, final Optional<PostgresRuntimeIdentity> runtimeIdentity) {
        return new PostgresRuntimeManifest(postgresqlVersion, DOWNLOADED, runtimeIdentity);
    }

    private static String requireNotBlank(final String value, final String fieldName) {
        final String requiredValue = Objects.requireNonNull(value, fieldName);
        if (requiredValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return requiredValue;
    }

    private static String requireRuntimeSource(final String runtimeSource) {
        final String validatedRuntimeSource = requireNotBlank(runtimeSource, "runtimeSource");
        if (!VALID_RUNTIME_SOURCES.contains(validatedRuntimeSource)) {
            throw new IllegalArgumentException("runtimeSource must be one of " + VALID_RUNTIME_SOURCES);
        }

        return validatedRuntimeSource;
    }

    private static void requireDownloadedChecksum(
            final String runtimeSource, final Optional<PostgresRuntimeIdentity> runtimeIdentity) {
        if (DOWNLOADED.equals(runtimeSource) && runtimeIdentity.isEmpty()) {
            throw new IllegalArgumentException("downloaded runtime manifest requires an identity checksum");
        }
    }
}
