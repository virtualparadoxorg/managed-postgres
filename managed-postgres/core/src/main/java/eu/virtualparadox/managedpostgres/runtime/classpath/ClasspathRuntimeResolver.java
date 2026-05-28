package eu.virtualparadox.managedpostgres.runtime.classpath;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.internal.runtime.CachedRuntimeResolver;
import eu.virtualparadox.managedpostgres.internal.runtime.ResolvedRuntime;
import eu.virtualparadox.managedpostgres.internal.runtime.TelemetryRuntimeResolver;
import eu.virtualparadox.managedpostgres.internal.runtime.signature.RuntimeSignatureVerifier;
import eu.virtualparadox.managedpostgres.runtime.RuntimeResolver;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheCleaner;
import eu.virtualparadox.managedpostgres.runtime.download.cleanup.RuntimeCacheRetention;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves classpath PostgreSQL runtime archives through the safe runtime cache.
 */
public final class ClasspathRuntimeResolver implements RuntimeResolver, TelemetryRuntimeResolver {

    private final ClasspathRuntimeCachePublisher publisher;
    private final CachedRuntimeResolver cacheResolver;

    /**
     * Creates a classpath runtime resolver using the resolver classloader.
     */
    public ClasspathRuntimeResolver() {
        this(resolverClassLoader());
    }

    /**
     * Creates a classpath runtime resolver.
     *
     * @param classLoader classloader used to read runtime resources
     */
    public ClasspathRuntimeResolver(final ClassLoader classLoader) {
        this(
                new RuntimeCacheCleaner(),
                new RuntimeCacheRetention(),
                new ClasspathRuntimeCachePublisher(classLoader),
                new RuntimeSignatureVerifier());
    }

    private ClasspathRuntimeResolver(
            final RuntimeCacheCleaner cleaner,
            final RuntimeCacheRetention retention,
            final ClasspathRuntimeCachePublisher publisher,
            final RuntimeSignatureVerifier signatureVerifier) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.cacheResolver = new CachedRuntimeResolver(cleaner, retention, signatureVerifier);
    }

    /**
     * Resolves a classpath runtime source using an internal placeholder version.
     *
     * @param runtimeSource classpath runtime source
     * @return resolved local PostgreSQL runtime directory
     */
    @Override
    public Path resolve(final RuntimeSource runtimeSource) {
        return resolve(runtimeSource, "unknown");
    }

    /**
     * Resolves a classpath runtime source for the requested PostgreSQL version.
     *
     * @param runtimeSource classpath runtime source
     * @param postgresqlVersion requested PostgreSQL version
     * @return resolved local PostgreSQL runtime directory
     */
    @Override
    public Path resolve(final RuntimeSource runtimeSource, final String postgresqlVersion) {
        return resolveWithTelemetry(runtimeSource, postgresqlVersion).runtimeDirectory();
    }

    /**
     * Resolves a classpath runtime source and returns internal install telemetry.
     *
     * @param runtimeSource classpath runtime source
     * @param postgresqlVersion requested PostgreSQL version
     * @return resolved local PostgreSQL runtime directory plus install timing
     */
    @Override
    public ResolvedRuntime resolveWithTelemetry(final RuntimeSource runtimeSource, final String postgresqlVersion) {
        final ClasspathRuntimeResolutionContext context = ClasspathRuntimeResolutionContext.create(
                runtimeSource,
                postgresqlVersion);
        return cacheResolver.resolveWithTelemetry(
                context.layout(),
                context.finalRuntime(),
                context.signature(),
                context.runtimeCache(),
                () -> publisher.publish(context));
    }

    private static ClassLoader resolverClassLoader() {
        return Objects.requireNonNull(ClasspathRuntimeResolver.class.getClassLoader(), "resolver classloader");
    }
}
