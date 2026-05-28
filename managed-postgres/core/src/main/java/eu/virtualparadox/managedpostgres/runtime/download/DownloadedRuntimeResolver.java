package eu.virtualparadox.managedpostgres.runtime.download;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.internal.runtime.CachedRuntimeResolver;
import eu.virtualparadox.managedpostgres.internal.runtime.ResolvedRuntime;
import eu.virtualparadox.managedpostgres.internal.runtime.TelemetryRuntimeResolver;
import eu.virtualparadox.managedpostgres.internal.runtime.signature.RuntimeSignatureVerifier;
import eu.virtualparadox.managedpostgres.runtime.RuntimeResolver;
import eu.virtualparadox.managedpostgres.runtime.download.cleanup.RuntimeCacheRetention;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves downloaded PostgreSQL runtime archives through a safe cache pipeline.
 */
public final class DownloadedRuntimeResolver implements RuntimeResolver, TelemetryRuntimeResolver {

    private final DownloadedRuntimeCachePublisher publisher;
    private final CachedRuntimeResolver cacheResolver;

    /**
     * Creates a downloaded runtime resolver with default collaborators.
     */
    public DownloadedRuntimeResolver() {
        this(new FileRuntimeArtifactDownloader());
    }

    /**
     * Creates a DownloadedRuntimeResolver instance.
     *
     * @param downloader downloader value
     */
    public DownloadedRuntimeResolver(final RuntimeArtifactDownloader downloader) {
        this(
                new RuntimeCacheCleaner(),
                new RuntimeCacheRetention(),
                new DownloadedRuntimeCachePublisher(downloader),
                new RuntimeSignatureVerifier());
    }

    private DownloadedRuntimeResolver(
            final RuntimeCacheCleaner cleaner,
            final RuntimeCacheRetention retention,
            final DownloadedRuntimeCachePublisher publisher,
            final RuntimeSignatureVerifier signatureVerifier) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.cacheResolver = new CachedRuntimeResolver(cleaner, retention, signatureVerifier);
    }

    /**
     * Resolves a downloaded runtime source using an internal placeholder version.
     *
     * @param runtimeSource downloaded runtime source
     * @return resolved local PostgreSQL runtime directory
     */
    @Override
    public Path resolve(final RuntimeSource runtimeSource) {
        return resolve(runtimeSource, "unknown");
    }

    /**
     * Resolves a downloaded runtime source for the requested PostgreSQL version.
     *
     * @param runtimeSource downloaded runtime source
     * @param postgresqlVersion requested PostgreSQL version
     * @return resolved local PostgreSQL runtime directory
     */
    @Override
    public Path resolve(final RuntimeSource runtimeSource, final String postgresqlVersion) {
        return resolveWithTelemetry(runtimeSource, postgresqlVersion).runtimeDirectory();
    }

    /**
     * Resolves a downloaded runtime source and returns internal install telemetry.
     *
     * @param runtimeSource downloaded runtime source
     * @param postgresqlVersion requested PostgreSQL version
     * @return resolved local PostgreSQL runtime directory plus install timing
     */
    @Override
    public ResolvedRuntime resolveWithTelemetry(final RuntimeSource runtimeSource, final String postgresqlVersion) {
        final DownloadedRuntimeResolutionContext context = DownloadedRuntimeResolutionContext.create(
                runtimeSource,
                postgresqlVersion);
        return cacheResolver.resolveWithTelemetry(
                context.layout(),
                context.finalRuntime(),
                context.signature(),
                context.runtimeCache(),
                () -> publisher.publish(context));
    }
}
