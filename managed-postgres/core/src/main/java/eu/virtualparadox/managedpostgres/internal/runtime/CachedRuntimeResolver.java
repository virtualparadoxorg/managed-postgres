package eu.virtualparadox.managedpostgres.internal.runtime;

import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import eu.virtualparadox.managedpostgres.internal.runtime.signature.RuntimeSignatureVerifier;
import eu.virtualparadox.managedpostgres.runtime.RuntimeValidator;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheCleaner;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheLayout;
import eu.virtualparadox.managedpostgres.runtime.download.cleanup.RuntimeCacheRetention;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Shared internal runtime cache resolver for archive-backed PostgreSQL runtimes.
 */
public final class CachedRuntimeResolver {

    private final RuntimeCacheCleaner cleaner;
    private final RuntimeCacheRetention retention;
    private final RuntimeSignatureVerifier signatureVerifier;

    /**
     * Creates an internal cached runtime resolver.
     *
     * @param cleaner cache cleanup service
     * @param retention cache retention service
     * @param signatureVerifier signed cache marker verifier
     */
    public CachedRuntimeResolver(
            final RuntimeCacheCleaner cleaner,
            final RuntimeCacheRetention retention,
            final RuntimeSignatureVerifier signatureVerifier) {
        this.cleaner = Objects.requireNonNull(cleaner, "cleaner");
        this.retention = Objects.requireNonNull(retention, "retention");
        this.signatureVerifier = Objects.requireNonNull(signatureVerifier, "signatureVerifier");
    }

    /**
     * Resolves a runtime from cache or publishes it through the supplied publisher.
     *
     * @param layout runtime cache layout
     * @param finalRuntime final runtime directory
     * @param signature optional runtime signature
     * @param runtimeCache runtime cache configuration
     * @param publisher publisher used on cache miss
     * @return resolved PostgreSQL runtime directory
     */
    public Path resolve(
            final RuntimeCacheLayout layout,
            final Path finalRuntime,
            final Optional<RuntimeSignature> signature,
            final RuntimeCache runtimeCache,
            final Supplier<Path> publisher) {
        return resolveWithTelemetry(layout, finalRuntime, signature, runtimeCache, publisher).runtimeDirectory();
    }

    /**
     * Resolves a runtime from cache or publishes it through the supplied publisher with internal timing telemetry.
     *
     * @param layout runtime cache layout
     * @param finalRuntime final runtime directory
     * @param signature optional runtime signature
     * @param runtimeCache runtime cache configuration
     * @param publisher publisher used on cache miss
     * @return resolved PostgreSQL runtime directory plus install timing
     */
    public ResolvedRuntime resolveWithTelemetry(
            final RuntimeCacheLayout layout,
            final Path finalRuntime,
            final Optional<RuntimeSignature> signature,
            final RuntimeCache runtimeCache,
            final Supplier<Path> publisher) {
        final RuntimeCacheLayout checkedLayout = Objects.requireNonNull(layout, "layout");
        final Path checkedFinalRuntime = Objects.requireNonNull(finalRuntime, "finalRuntime");
        final Optional<RuntimeSignature> checkedSignature = Objects.requireNonNull(signature, "signature");
        final RuntimeCache checkedRuntimeCache = Objects.requireNonNull(runtimeCache, "runtimeCache");
        final Supplier<Path> checkedPublisher = Objects.requireNonNull(publisher, "publisher");

        cleaner.clean(checkedLayout);
        final ResolvedRuntime resolvedRuntime = resolveRuntimeDirectory(
                checkedFinalRuntime,
                checkedSignature,
                checkedPublisher);
        retention.retain(checkedLayout, resolvedRuntime.runtimeDirectory(), checkedRuntimeCache.retainedVersions());

        return resolvedRuntime;
    }

    private ResolvedRuntime resolveRuntimeDirectory(
            final Path finalRuntime,
            final Optional<RuntimeSignature> signature,
            final Supplier<Path> publisher) {
        final ResolvedRuntime resolvedRuntime;
        if (Files.exists(finalRuntime)) {
            signature.ifPresent(runtimeSignature -> signatureVerifier.requireVerifiedMarker(
                    finalRuntime,
                    runtimeSignature));
            resolvedRuntime = new ResolvedRuntime(
                    RuntimeValidator.requireUsableRuntimeDirectory(finalRuntime),
                    Duration.ZERO);
        } else {
            final long installStartedAt = System.nanoTime();
            resolvedRuntime = new ResolvedRuntime(
                    publisher.get(),
                    Duration.ofNanos(System.nanoTime() - installStartedAt));
        }

        return resolvedRuntime;
    }
}
