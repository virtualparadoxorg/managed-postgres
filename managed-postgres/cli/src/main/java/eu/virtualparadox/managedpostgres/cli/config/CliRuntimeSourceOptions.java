package eu.virtualparadox.managedpostgres.cli.config;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable direct CLI runtime-source option values.
 *
 * @param source runtime source name
 * @param path existing runtime path
 * @param repository downloaded runtime repository URI
 * @param resource classpath runtime archive resource
 * @param checksum expected runtime archive checksum
 * @param signaturePublicKey runtime artifact signature public key
 * @param signature runtime artifact detached signature
 * @param cache runtime cache root
 */
public record CliRuntimeSourceOptions(
        Optional<String> source,
        Optional<Path> path,
        Optional<String> repository,
        Optional<String> resource,
        Optional<String> checksum,
        Optional<String> signaturePublicKey,
        Optional<String> signature,
        Optional<Path> cache) {

    /**
     * Creates immutable direct CLI runtime-source option values.
     *
     * @param source runtime source name
     * @param path existing runtime path
     * @param repository downloaded runtime repository URI
     * @param resource classpath runtime archive resource
     * @param checksum expected runtime archive checksum
     * @param signaturePublicKey runtime artifact signature public key
     * @param signature runtime artifact detached signature
     * @param cache runtime cache root
     */
    public CliRuntimeSourceOptions {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(checksum, "checksum");
        Objects.requireNonNull(signaturePublicKey, "signaturePublicKey");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(cache, "cache");
    }

    /**
     * Returns empty runtime-source option values.
     *
     * @return empty runtime-source option values
     */
    public static CliRuntimeSourceOptions empty() {
        return new CliRuntimeSourceOptions(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /**
     * Returns runtime-source option values from legacy source/path inputs.
     *
     * @param source runtime source name
     * @param path existing runtime path
     * @return runtime-source option values
     */
    public static CliRuntimeSourceOptions sourceAndPath(
            final Optional<String> source,
            final Optional<Path> path) {
        return empty()
                .withOptionalSource(source)
                .withOptionalPath(path);
    }

    /**
     * Returns this option set with another runtime source name.
     *
     * @param value runtime source name
     * @return updated runtime-source options
     */
    public CliRuntimeSourceOptions withSource(final String value) {
        return new CliRuntimeSourceOptions(
                Optional.of(value),
                path,
                repository,
                resource,
                checksum,
                signaturePublicKey,
                signature,
                cache);
    }

    private CliRuntimeSourceOptions withOptionalSource(final Optional<String> value) {
        return new CliRuntimeSourceOptions(
                Objects.requireNonNull(value, "value"),
                path,
                repository,
                resource,
                checksum,
                signaturePublicKey,
                signature,
                cache);
    }

    /**
     * Returns this option set with another existing runtime path.
     *
     * @param value existing runtime path
     * @return updated runtime-source options
     */
    public CliRuntimeSourceOptions withPath(final Path value) {
        return new CliRuntimeSourceOptions(
                source,
                Optional.of(value),
                repository,
                resource,
                checksum,
                signaturePublicKey,
                signature,
                cache);
    }

    private CliRuntimeSourceOptions withOptionalPath(final Optional<Path> value) {
        return new CliRuntimeSourceOptions(
                source,
                Objects.requireNonNull(value, "value"),
                repository,
                resource,
                checksum,
                signaturePublicKey,
                signature,
                cache);
    }

    /**
     * Returns this option set with another downloaded runtime repository URI.
     *
     * @param value downloaded runtime repository URI
     * @return updated runtime-source options
     */
    public CliRuntimeSourceOptions withRepository(final String value) {
        return new CliRuntimeSourceOptions(
                source,
                path,
                Optional.of(value),
                resource,
                checksum,
                signaturePublicKey,
                signature,
                cache);
    }

    /**
     * Returns this option set with another classpath runtime archive resource.
     *
     * @param value classpath runtime archive resource
     * @return updated runtime-source options
     */
    public CliRuntimeSourceOptions withResource(final String value) {
        return new CliRuntimeSourceOptions(
                source,
                path,
                repository,
                Optional.of(value),
                checksum,
                signaturePublicKey,
                signature,
                cache);
    }

    /**
     * Returns this option set with another expected runtime archive checksum.
     *
     * @param value expected runtime archive checksum
     * @return updated runtime-source options
     */
    public CliRuntimeSourceOptions withChecksum(final String value) {
        return new CliRuntimeSourceOptions(
                source,
                path,
                repository,
                resource,
                Optional.of(value),
                signaturePublicKey,
                signature,
                cache);
    }

    /**
     * Returns this option set with another runtime artifact signature public key.
     *
     * @param value runtime artifact signature public key
     * @return updated runtime-source options
     */
    public CliRuntimeSourceOptions withSignaturePublicKey(final String value) {
        return new CliRuntimeSourceOptions(
                source,
                path,
                repository,
                resource,
                checksum,
                Optional.of(value),
                signature,
                cache);
    }

    /**
     * Returns this option set with another runtime artifact detached signature.
     *
     * @param value runtime artifact detached signature
     * @return updated runtime-source options
     */
    public CliRuntimeSourceOptions withSignature(final String value) {
        return new CliRuntimeSourceOptions(
                source,
                path,
                repository,
                resource,
                checksum,
                signaturePublicKey,
                Optional.of(value),
                cache);
    }

    /**
     * Returns this option set with another runtime cache root.
     *
     * @param value runtime cache root
     * @return updated runtime-source options
     */
    public CliRuntimeSourceOptions withCache(final Path value) {
        return new CliRuntimeSourceOptions(
                source,
                path,
                repository,
                resource,
                checksum,
                signaturePublicKey,
                signature,
                Optional.of(value));
    }

    /**
     * Returns whether direct CLI runtime-source values are present.
     *
     * @return true when any direct runtime-source option is present
     */
    public boolean hasValues() {
        final boolean hasValues = source.isPresent()
                || path.isPresent()
                || repository.isPresent()
                || resource.isPresent()
                || checksum.isPresent()
                || signaturePublicKey.isPresent()
                || signature.isPresent()
                || cache.isPresent();

        return hasValues;
    }
}
