package eu.virtualparadox.managedpostgres.runtime.packaging;

import java.net.URI;
import java.util.Objects;

/**
 * PostgreSQL release version selected for runtime bundle production.
 *
 * @param majorVersion PostgreSQL major version line
 * @param version semantic PostgreSQL version text
 * @param sourceTarball canonical source tarball URI
 * @param sourceTarballSha256 trusted source tarball SHA-256 checksum
 */
public record PostgresRelease(int majorVersion, String version, URI sourceTarball, String sourceTarballSha256) {

    /**
     * Creates release metadata.
     *
     * @param majorVersion PostgreSQL major version line
     * @param version semantic PostgreSQL version text
     * @param sourceTarball canonical source tarball URI
     * @param sourceTarballSha256 trusted source tarball SHA-256 checksum
     */
    public PostgresRelease {
        final URI validatedSourceTarball = Objects.requireNonNull(sourceTarball, "sourceTarball");
        if (majorVersion <= 0) {
            throw new IllegalArgumentException("majorVersion must be positive");
        }
        final String validatedVersion = Objects.requireNonNull(version, "version");
        final String validatedSourceTarballSha256 = Objects.requireNonNull(sourceTarballSha256, "sourceTarballSha256");
        if (validatedVersion.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (validatedSourceTarballSha256.isBlank()) {
            throw new IllegalArgumentException("sourceTarballSha256 must not be blank");
        }
        version = validatedVersion;
        sourceTarball = validatedSourceTarball;
        sourceTarballSha256 = validatedSourceTarballSha256;
    }
}
