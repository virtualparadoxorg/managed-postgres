package eu.virtualparadox.managedpostgres.config;

import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Immutable downloaded PostgreSQL runtime configuration.
 *
 * <p>This type captures supply-chain choices that users may configure without
 * exposing platform classifiers, archive extraction, or filesystem staging
 * internals.
 *
 * @param repository optional runtime repository
 * @param cache optional runtime cache root
 * @param checksum optional expected artifact checksum
 * @param signature optional detached artifact signature
 */
public record DownloadedRuntime(
        Optional<RuntimeRepository> repository,
        Optional<RuntimeCache> cache,
        Optional<String> checksum,
        Optional<RuntimeSignature> signature) {

    /**
     * Creates downloaded runtime configuration.
     *
     * @param repository optional runtime repository
     * @param cache optional runtime cache root
     * @param checksum optional expected artifact checksum
     * @param signature optional detached artifact signature
     */
    public DownloadedRuntime {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(cache, "cache");
        checksum = requireChecksum(Objects.requireNonNull(checksum, "checksum"));
        Objects.requireNonNull(signature, "signature");
    }

    /**
     * Creates downloaded runtime configuration.
     *
     * @param repository optional runtime repository
     * @param cache optional runtime cache root
     * @param checksum optional expected artifact checksum
     */
    public DownloadedRuntime(
            final Optional<RuntimeRepository> repository,
            final Optional<RuntimeCache> cache,
            final Optional<String> checksum) {
        this(repository, cache, checksum, Optional.empty());
    }

    /**
     * Creates empty downloaded runtime configuration.
     *
     * @return empty downloaded runtime configuration
     */
    public static DownloadedRuntime empty() {
        return new DownloadedRuntime(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Returns a copy with runtime repository configuration.
     *
     * @param newRepository runtime repository configuration
     * @return updated downloaded runtime configuration
     */
    public DownloadedRuntime repository(final RuntimeRepository newRepository) {
        return new DownloadedRuntime(Optional.of(newRepository), cache, checksum, signature);
    }

    /**
     * Returns a copy with runtime repository configuration.
     *
     * @param newRepository runtime repository configuration
     * @return updated downloaded runtime configuration
     */
    public DownloadedRuntime withRepository(final RuntimeRepository newRepository) {
        return repository(newRepository);
    }

    /**
     * Returns a copy with runtime cache configuration.
     *
     * @param newCache runtime cache configuration
     * @return updated downloaded runtime configuration
     */
    public DownloadedRuntime cache(final RuntimeCache newCache) {
        return new DownloadedRuntime(repository, Optional.of(newCache), checksum, signature);
    }

    /**
     * Returns a copy with runtime cache configuration.
     *
     * @param newCache runtime cache configuration
     * @return updated downloaded runtime configuration
     */
    public DownloadedRuntime withCache(final RuntimeCache newCache) {
        return cache(newCache);
    }

    /**
     * Returns a copy with expected artifact checksum.
     *
     * @param newChecksum expected artifact checksum
     * @return updated downloaded runtime configuration
     */
    public DownloadedRuntime checksum(final String newChecksum) {
        return new DownloadedRuntime(
                repository, cache, Optional.of(requireNotBlank(newChecksum, "checksum")), signature);
    }

    /**
     * Returns a copy with expected artifact checksum.
     *
     * @param newChecksum expected artifact checksum
     * @return updated downloaded runtime configuration
     */
    public DownloadedRuntime withChecksum(final String newChecksum) {
        return checksum(newChecksum);
    }

    /**
     * Returns a copy with detached artifact signature verification.
     *
     * @param newSignature detached artifact signature
     * @return updated downloaded runtime configuration
     */
    public DownloadedRuntime signature(final RuntimeSignature newSignature) {
        return new DownloadedRuntime(
                repository, cache, checksum, Optional.of(Objects.requireNonNull(newSignature, "newSignature")));
    }

    /**
     * Returns a copy with detached artifact signature verification.
     *
     * @param newSignature detached artifact signature
     * @return updated downloaded runtime configuration
     */
    public DownloadedRuntime withSignature(final RuntimeSignature newSignature) {
        return signature(newSignature);
    }

    private static Optional<String> requireChecksum(final Optional<String> checksum) {
        final Optional<String> checkedChecksum;
        if (checksum.isPresent()) {
            checkedChecksum = Optional.of(requireNotBlank(checksum.orElseThrow(), "checksum"));
        } else {
            checkedChecksum = checksum;
        }

        return checkedChecksum;
    }

    private static String requireNotBlank(final String value, final String fieldName) {
        final String checkedValue = Objects.requireNonNull(value, fieldName);
        if (StringUtils.isBlank(checkedValue)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return checkedValue;
    }
}
