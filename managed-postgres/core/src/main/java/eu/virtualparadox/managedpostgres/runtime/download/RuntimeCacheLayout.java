package eu.virtualparadox.managedpostgres.runtime.download;

import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import eu.virtualparadox.managedpostgres.runtime.Checksum;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolves framework-owned cache paths for downloaded PostgreSQL runtime artifacts.
 */
public final class RuntimeCacheLayout {

    private static final int CHECKSUM_SEGMENT_LENGTH = 12;
    private static final Pattern VERSION_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final Path cacheRoot;

    /**
     * Creates a cache layout rooted at the supplied directory.
     *
     * @param cacheRoot framework-owned cache root
     */
    public RuntimeCacheLayout(final Path cacheRoot) {
        this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot")
                .toAbsolutePath()
                .normalize();
    }

    /**
     * Returns the framework-owned cache root.
     *
     * @return normalized cache root
     */
    public Path cacheRoot() {
        return cacheRoot;
    }

    /**
     * Returns the directory for partial and downloaded runtime archives.
     *
     * @return downloads directory
     */
    public Path downloadsDirectory() {
        return cacheRoot.resolve("downloads");
    }

    /**
     * Returns the directory for extracted runtime directories.
     *
     * @return runtimes directory
     */
    public Path runtimesDirectory() {
        return cacheRoot.resolve("runtimes");
    }

    /**
     * Resolves the partial download file for a runtime archive.
     *
     * @param postgresqlVersion PostgreSQL version
     * @param checksum expected runtime archive checksum
     * @return partial download path
     */
    public Path downloadFile(final String postgresqlVersion, final Checksum checksum) {
        return downloadFile(postgresqlVersion, checksum, Optional.empty());
    }

    /**
     * Resolves the partial download file for a signed runtime archive.
     *
     * @param postgresqlVersion PostgreSQL version
     * @param checksum expected runtime archive checksum
     * @param signature configured signature
     * @return partial download path
     */
    public Path downloadFile(
            final String postgresqlVersion,
            final Checksum checksum,
            final RuntimeSignature signature) {
        return downloadFile(postgresqlVersion, checksum, Optional.of(signature));
    }

    /**
     * Resolves the partial download file for a runtime archive.
     *
     * @param postgresqlVersion PostgreSQL version
     * @param checksum expected runtime archive checksum
     * @param signature configured signature, when present
     * @return partial download path
     */
    public Path downloadFile(
            final String postgresqlVersion,
            final Checksum checksum,
            final Optional<RuntimeSignature> signature) {
        return downloadsDirectory().resolve(cacheName(postgresqlVersion, checksum, signature) + ".zip.download");
    }

    /**
     * Resolves the final extracted runtime directory.
     *
     * @param postgresqlVersion PostgreSQL version
     * @param checksum expected runtime archive checksum
     * @return final runtime directory
     */
    public Path runtimeDirectory(final String postgresqlVersion, final Checksum checksum) {
        return runtimeDirectory(postgresqlVersion, checksum, Optional.empty());
    }

    /**
     * Resolves the final extracted signed runtime directory.
     *
     * @param postgresqlVersion PostgreSQL version
     * @param checksum expected runtime archive checksum
     * @param signature configured signature
     * @return final runtime directory
     */
    public Path runtimeDirectory(
            final String postgresqlVersion,
            final Checksum checksum,
            final RuntimeSignature signature) {
        return runtimeDirectory(postgresqlVersion, checksum, Optional.of(signature));
    }

    /**
     * Resolves the final extracted runtime directory.
     *
     * @param postgresqlVersion PostgreSQL version
     * @param checksum expected runtime archive checksum
     * @param signature configured signature, when present
     * @return final runtime directory
     */
    public Path runtimeDirectory(
            final String postgresqlVersion,
            final Checksum checksum,
            final Optional<RuntimeSignature> signature) {
        return runtimesDirectory().resolve(cacheName(postgresqlVersion, checksum, signature));
    }

    /**
     * Resolves the staging directory used while extracting a runtime.
     *
     * @param postgresqlVersion PostgreSQL version
     * @param checksum expected runtime archive checksum
     * @return staging directory
     */
    public Path stagingDirectory(final String postgresqlVersion, final Checksum checksum) {
        return stagingDirectory(postgresqlVersion, checksum, Optional.empty());
    }

    /**
     * Resolves the staging directory used while extracting a signed runtime.
     *
     * @param postgresqlVersion PostgreSQL version
     * @param checksum expected runtime archive checksum
     * @param signature configured signature
     * @return staging directory
     */
    public Path stagingDirectory(
            final String postgresqlVersion,
            final Checksum checksum,
            final RuntimeSignature signature) {
        return stagingDirectory(postgresqlVersion, checksum, Optional.of(signature));
    }

    /**
     * Resolves the staging directory used while extracting a runtime.
     *
     * @param postgresqlVersion PostgreSQL version
     * @param checksum expected runtime archive checksum
     * @param signature configured signature, when present
     * @return staging directory
     */
    public Path stagingDirectory(
            final String postgresqlVersion,
            final Checksum checksum,
            final Optional<RuntimeSignature> signature) {
        return runtimesDirectory().resolve(cacheName(postgresqlVersion, checksum, signature) + ".staging");
    }

    private static String cacheName(
            final String postgresqlVersion,
            final Checksum checksum,
            final Optional<RuntimeSignature> signature) {
        final String checkedVersion = requireSafeVersion(postgresqlVersion);
        final Checksum checkedChecksum = Objects.requireNonNull(checksum, "checksum");
        final Optional<RuntimeSignature> checkedSignature = Objects.requireNonNull(signature, "signature");

        final String cacheName = "postgres-"
                + checkedVersion
                + "-"
                + checkedChecksum.algorithm()
                + "-"
                + checksumSegment(checkedChecksum);

        return checkedSignature
                .map(value -> cacheName + "-sig-" + value.markerFingerprint())
                .orElse(cacheName);
    }

    private static String checksumSegment(final Checksum checksum) {
        return checksum.hex().substring(0, CHECKSUM_SEGMENT_LENGTH);
    }

    private static String requireSafeVersion(final String postgresqlVersion) {
        final String checkedVersion = Objects.requireNonNull(postgresqlVersion, "postgresqlVersion");
        if (StringUtils.isBlank(checkedVersion) || !VERSION_PATTERN.matcher(checkedVersion).matches()) {
            throw new IllegalArgumentException("postgresql version must be a safe cache path segment");
        }

        return checkedVersion;
    }
}
