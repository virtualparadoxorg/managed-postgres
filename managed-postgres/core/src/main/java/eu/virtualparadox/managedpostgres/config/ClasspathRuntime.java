package eu.virtualparadox.managedpostgres.config;

import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Immutable classpath PostgreSQL runtime archive configuration.
 *
 * @param resource classpath resource containing a ZIP runtime archive
 * @param cache runtime cache override, when configured
 * @param checksum expected runtime archive checksum, when configured
 * @param signature detached artifact signature, when configured
 */
public record ClasspathRuntime(
        String resource,
        Optional<RuntimeCache> cache,
        Optional<String> checksum,
        Optional<RuntimeSignature> signature) {

    /**
     * Creates immutable classpath runtime archive configuration.
     *
     * @param resource classpath resource containing a ZIP runtime archive
     * @param cache runtime cache override, when configured
     * @param checksum expected runtime archive checksum, when configured
     * @param signature detached artifact signature, when configured
     */
    public ClasspathRuntime {
        resource = requireResource(resource);
        Objects.requireNonNull(cache, "cache");
        final Optional<String> validatedChecksum = Objects.requireNonNull(checksum, "checksum")
                .map(ClasspathRuntime::requireChecksum);
        checksum = validatedChecksum;
        Objects.requireNonNull(signature, "signature");
    }

    /**
     * Creates immutable classpath runtime archive configuration.
     *
     * @param resource classpath resource containing a ZIP runtime archive
     * @param cache runtime cache override, when configured
     * @param checksum expected runtime archive checksum, when configured
     */
    public ClasspathRuntime(
            final String resource,
            final Optional<RuntimeCache> cache,
            final Optional<String> checksum) {
        this(resource, cache, checksum, Optional.empty());
    }

    /**
     * Creates classpath runtime configuration for a resource.
     *
     * @param resource classpath resource containing a ZIP runtime archive
     * @return classpath runtime configuration
     */
    public static ClasspathRuntime resource(final String resource) {
        return new ClasspathRuntime(resource, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Returns a copy with the supplied runtime cache.
     *
     * @param newCache runtime cache
     * @return updated classpath runtime configuration
     */
    public ClasspathRuntime cache(final RuntimeCache newCache) {
        return new ClasspathRuntime(
                resource,
                Optional.of(Objects.requireNonNull(newCache, "newCache")),
                checksum,
                signature);
    }

    /**
     * Returns a copy with the supplied runtime cache.
     *
     * @param newCache runtime cache
     * @return updated classpath runtime configuration
     */
    public ClasspathRuntime withCache(final RuntimeCache newCache) {
        return cache(newCache);
    }

    /**
     * Returns a copy with the supplied checksum.
     *
     * @param newChecksum expected runtime archive checksum
     * @return updated classpath runtime configuration
     */
    public ClasspathRuntime checksum(final String newChecksum) {
        return new ClasspathRuntime(resource, cache, Optional.of(requireChecksum(newChecksum)), signature);
    }

    /**
     * Returns a copy with the supplied checksum.
     *
     * @param newChecksum expected runtime archive checksum
     * @return updated classpath runtime configuration
     */
    public ClasspathRuntime withChecksum(final String newChecksum) {
        return checksum(newChecksum);
    }

    /**
     * Returns a copy with detached artifact signature verification.
     *
     * @param newSignature detached artifact signature
     * @return updated classpath runtime configuration
     */
    public ClasspathRuntime signature(final RuntimeSignature newSignature) {
        return new ClasspathRuntime(
                resource,
                cache,
                checksum,
                Optional.of(Objects.requireNonNull(newSignature, "newSignature")));
    }

    /**
     * Returns a copy with detached artifact signature verification.
     *
     * @param newSignature detached artifact signature
     * @return updated classpath runtime configuration
     */
    public ClasspathRuntime withSignature(final RuntimeSignature newSignature) {
        return signature(newSignature);
    }

    private static String requireResource(final String resource) {
        if (StringUtils.isBlank(resource)) {
            throw new IllegalArgumentException("classpath runtime resource must not be blank");
        }

        return resource;
    }

    private static String requireChecksum(final String checksum) {
        if (StringUtils.isBlank(checksum)) {
            throw new IllegalArgumentException("classpath runtime checksum must not be blank");
        }

        return checksum;
    }
}
