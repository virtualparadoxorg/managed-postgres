package eu.virtualparadox.managedpostgres.runtime.classpath;

import eu.virtualparadox.managedpostgres.config.ClasspathRuntime;
import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.runtime.RuntimeSignature;
import eu.virtualparadox.managedpostgres.runtime.Checksum;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheLayout;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Captures classpath runtime resolution details for managed PostgreSQL internals.
 *
 * @param runtimeSource runtime source
 * @param classpathRuntime classpath runtime configuration
 * @param postgresqlVersion requested PostgreSQL version
 * @param resourceName classloader resource name
 * @param checksum expected runtime archive checksum
 * @param signature detached artifact signature, when configured
 * @param runtimeCache runtime cache configuration
 * @param layout runtime cache layout
 * @param finalRuntime final runtime directory
 */
record ClasspathRuntimeResolutionContext(
        RuntimeSource runtimeSource,
        ClasspathRuntime classpathRuntime,
        String postgresqlVersion,
        String resourceName,
        Checksum checksum,
        Optional<RuntimeSignature> signature,
        RuntimeCache runtimeCache,
        RuntimeCacheLayout layout,
        Path finalRuntime) {

    private static final String CLASSPATH = "classpath";
    private static final String DEFAULT_CACHE_NAMESPACE = "managed-postgres";

    /**
     * Creates immutable classpath runtime resolution context.
     *
     * @param runtimeSource runtime source
     * @param classpathRuntime classpath runtime configuration
     * @param postgresqlVersion requested PostgreSQL version
     * @param resourceName classloader resource name
     * @param checksum expected runtime archive checksum
     * @param layout runtime cache layout
     * @param finalRuntime final runtime directory
     */
    ClasspathRuntimeResolutionContext {
        Objects.requireNonNull(runtimeSource, "runtimeSource");
        Objects.requireNonNull(classpathRuntime, "classpathRuntime");
        Objects.requireNonNull(postgresqlVersion, "postgresqlVersion");
        Objects.requireNonNull(resourceName, "resourceName");
        Objects.requireNonNull(checksum, "checksum");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(runtimeCache, "runtimeCache");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(finalRuntime, "finalRuntime");
    }

    /**
     * Creates a classpath runtime resolution context.
     *
     * @param runtimeSource runtime source
     * @param postgresqlVersion requested PostgreSQL version
     * @return resolution context
     */
    static ClasspathRuntimeResolutionContext create(
            final RuntimeSource runtimeSource,
            final String postgresqlVersion) {
        final RuntimeSource checkedRuntimeSource = Objects.requireNonNull(runtimeSource, "runtimeSource");
        if (!CLASSPATH.equals(checkedRuntimeSource.kind())) {
            throw new IllegalArgumentException("classpath runtime resolver requires a classpath runtime source");
        }

        final ClasspathRuntime runtime = checkedRuntimeSource.classpathRuntime()
                .orElseThrow(() -> ClasspathRuntimeResolutionDiagnostics.failure(
                        "classpath runtime configuration is missing",
                        checkedRuntimeSource));
        final Checksum checksum = checksum(runtime, checkedRuntimeSource);
        final Optional<RuntimeSignature> signature = runtime.signature();
        final RuntimeCache runtimeCache = cache(runtime);
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(runtimeCache.root());

        return new ClasspathRuntimeResolutionContext(
                checkedRuntimeSource,
                runtime,
                postgresqlVersion,
                resourceName(runtime.resource()),
                checksum,
                signature,
                runtimeCache,
                layout,
                layout.runtimeDirectory(postgresqlVersion, checksum, signature));
    }

    /**
     * Returns the copied classpath archive path.
     *
     * @return copied artifact path
     */
    Path artifact() {
        return layout.downloadFile(postgresqlVersion, checksum, signature);
    }

    /**
     * Returns the extraction staging directory.
     *
     * @return staging directory
     */
    Path staging() {
        return layout.stagingDirectory(postgresqlVersion, checksum, signature);
    }

    private static RuntimeCache cache(final ClasspathRuntime runtime) {
        return runtime.cache()
                .orElseGet(() -> RuntimeCache.userCache(DEFAULT_CACHE_NAMESPACE));
    }

    private static Checksum checksum(final ClasspathRuntime runtime, final RuntimeSource runtimeSource) {
        return Checksum.parse(runtime.checksum()
                .orElseThrow(() -> ClasspathRuntimeResolutionDiagnostics.failure(
                        "classpath runtime checksum is not configured",
                        runtimeSource)));
    }

    private static String resourceName(final String resource) {
        String resourceName = Objects.requireNonNull(resource, "resource");
        while (resourceName.startsWith("/")) {
            resourceName = resourceName.substring(1);
        }

        return resourceName;
    }
}
