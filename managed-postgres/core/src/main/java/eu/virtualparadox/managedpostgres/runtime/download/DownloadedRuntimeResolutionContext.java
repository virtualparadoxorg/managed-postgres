package eu.virtualparadox.managedpostgres.runtime.download;

import eu.virtualparadox.managedpostgres.config.DownloadedRuntime;
import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import eu.virtualparadox.managedpostgres.runtime.Checksum;

/**
 * Captures downloaded runtime resolution context details for managed PostgreSQL internals.
 *
 * @param runtimeSource runtime source value
 * @param repository repository value, when configured
 * @param postgresqlVersion postgresql version value
 * @param checksum checksum value
 * @param signature detached artifact signature, when configured
 * @param runtimeCache runtime cache configuration
 * @param layout layout value
 * @param finalRuntime final runtime value
 */
record DownloadedRuntimeResolutionContext(
        RuntimeSource runtimeSource,
        Optional<RuntimeRepository> repository,
        String postgresqlVersion,
        Checksum checksum,
        Optional<RuntimeSignature> signature,
        RuntimeCache runtimeCache,
        RuntimeCacheLayout layout,
        Path finalRuntime) {

    private static final String DOWNLOADED = "downloaded";
    private static final String DEFAULT_CACHE_NAMESPACE = "managed-postgres";

    /**
     * Defines the value value.
     */
    DownloadedRuntimeResolutionContext {
        Objects.requireNonNull(runtimeSource, "runtimeSource");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(postgresqlVersion, "postgresqlVersion");
        Objects.requireNonNull(checksum, "checksum");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(runtimeCache, "runtimeCache");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(finalRuntime, "finalRuntime");
    }

    /**
     * Returns the create result.
     *
     * @param runtimeSource runtime source value
     * @param postgresqlVersion postgresql version value
     * @return create result
     */
    static DownloadedRuntimeResolutionContext create(
            final RuntimeSource runtimeSource,
            final String postgresqlVersion) {
        final RuntimeSource checkedRuntimeSource = Objects.requireNonNull(runtimeSource, "runtimeSource");
        if (!DOWNLOADED.equals(checkedRuntimeSource.kind())) {
            throw new IllegalArgumentException("downloaded runtime resolver requires a downloaded runtime source");
        }

        final DownloadedRuntime runtime = checkedRuntimeSource.downloadedRuntime()
                .orElseThrow(() -> DownloadedRuntimeResolutionDiagnostics.failure(
                        "downloaded runtime configuration is missing",
                        checkedRuntimeSource));
        final Optional<RuntimeRepository> repository = runtime.repository();
        final Checksum checksum = checksum(runtime, checkedRuntimeSource);
        final Optional<RuntimeSignature> signature = runtime.signature();
        final RuntimeCache runtimeCache = cache(runtime);
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(runtimeCache.root());

        return new DownloadedRuntimeResolutionContext(
                checkedRuntimeSource,
                repository,
                postgresqlVersion,
                checksum,
                signature,
                runtimeCache,
                layout,
                layout.runtimeDirectory(postgresqlVersion, checksum, signature));
    }

    /**
     * Returns the download result.
     *
     * @return download result
     */
    Path download() {
        return layout.downloadFile(postgresqlVersion, checksum, signature);
    }

    /**
     * Returns the staging result.
     *
     * @return staging result
     */
    Path staging() {
        return layout.stagingDirectory(postgresqlVersion, checksum, signature);
    }

    private static RuntimeCache cache(final DownloadedRuntime runtime) {
        return runtime.cache()
                .orElseGet(() -> RuntimeCache.userCache(DEFAULT_CACHE_NAMESPACE));
    }

    private static Checksum checksum(final DownloadedRuntime runtime, final RuntimeSource runtimeSource) {
        return Checksum.parse(runtime.checksum()
                .orElseThrow(() -> DownloadedRuntimeResolutionDiagnostics.failure(
                        "downloaded runtime checksum is not configured",
                        runtimeSource)));
    }
}
