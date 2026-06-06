package eu.virtualparadox.managedpostgres.runtime;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.internal.runtime.ResolvedRuntime;
import eu.virtualparadox.managedpostgres.internal.runtime.TelemetryRuntimeResolver;
import eu.virtualparadox.managedpostgres.observe.ManagedPostgresProgressListener;
import eu.virtualparadox.managedpostgres.runtime.classpath.ClasspathRuntimeResolver;
import eu.virtualparadox.managedpostgres.runtime.download.DownloadedRuntimeResolver;
import eu.virtualparadox.managedpostgres.runtime.download.OfficialRuntimeSourceResolver;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Resolves supported PostgreSQL runtime source kinds.
 */
public final class DefaultRuntimeResolver implements RuntimeResolver, TelemetryRuntimeResolver {

    private static final String SYSTEM = "system";
    private static final String EXISTING = "existing";
    private static final String DOWNLOADED = "downloaded";
    private static final String CLASSPATH = "classpath";

    /**
     * Creates a default runtime resolver.
     */
    public DefaultRuntimeResolver() {}

    /**
     * Resolves the runtime directory for a configured runtime source.
     *
     * @param runtimeSource runtime source
     * @return usable runtime directory
     */
    @Override
    public Path resolve(final RuntimeSource runtimeSource) {
        return resolve(runtimeSource, "unknown");
    }

    /**
     * Resolves the runtime directory for a configured runtime source and requested PostgreSQL version.
     *
     * @param runtimeSource runtime source
     * @param postgresqlVersion requested PostgreSQL version
     * @return usable runtime directory
     */
    @Override
    public Path resolve(final RuntimeSource runtimeSource, final String postgresqlVersion) {
        return resolveWithTelemetry(runtimeSource, postgresqlVersion).runtimeDirectory();
    }

    /**
     * Resolves the runtime directory for a configured runtime source and requested PostgreSQL version.
     *
     * @param runtimeSource runtime source
     * @param postgresqlVersion requested PostgreSQL version
     * @return resolved runtime plus install telemetry
     */
    @Override
    public ResolvedRuntime resolveWithTelemetry(final RuntimeSource runtimeSource, final String postgresqlVersion) {
        return resolveWithTelemetry(runtimeSource, postgresqlVersion, ManagedPostgresProgressListener.none());
    }

    /**
     * Resolves the runtime directory for a configured runtime source and requested PostgreSQL version,
     * emitting download/verify/extract progress for archive-backed runtimes.
     *
     * @param runtimeSource runtime source
     * @param postgresqlVersion requested PostgreSQL version
     * @param progress startup progress listener
     * @return resolved runtime plus install telemetry
     */
    @Override
    public ResolvedRuntime resolveWithTelemetry(
            final RuntimeSource runtimeSource,
            final String postgresqlVersion,
            final ManagedPostgresProgressListener progress) {
        final RuntimeSource checkedRuntimeSource = Objects.requireNonNull(runtimeSource, "runtimeSource");
        final ManagedPostgresProgressListener checkedProgress = Objects.requireNonNull(progress, "progress");
        final ResolvedRuntime resolvedRuntime;
        if (SYSTEM.equals(checkedRuntimeSource.kind())) {
            resolvedRuntime =
                    new ResolvedRuntime(new SystemRuntimeResolver().resolve(checkedRuntimeSource), Duration.ZERO);
        } else if (EXISTING.equals(checkedRuntimeSource.kind())) {
            resolvedRuntime =
                    new ResolvedRuntime(new ExistingRuntimeResolver().resolve(checkedRuntimeSource), Duration.ZERO);
        } else if (DOWNLOADED.equals(checkedRuntimeSource.kind())) {
            final RuntimeSource downloadedSource =
                    new OfficialRuntimeSourceResolver().resolve(checkedRuntimeSource, postgresqlVersion);
            resolvedRuntime = new DownloadedRuntimeResolver()
                    .resolveWithTelemetry(downloadedSource, postgresqlVersion, checkedProgress);
        } else if (CLASSPATH.equals(checkedRuntimeSource.kind())) {
            resolvedRuntime = new ClasspathRuntimeResolver()
                    .resolveWithTelemetry(checkedRuntimeSource, postgresqlVersion, checkedProgress);
        } else {
            throw new IllegalArgumentException("unsupported runtime source kind: " + checkedRuntimeSource.kind());
        }

        return resolvedRuntime;
    }
}
