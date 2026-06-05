package eu.virtualparadox.managedpostgres.internal.runtime;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.observe.ManagedPostgresProgressListener;

/**
 * Internal runtime resolver extension that can expose resolution telemetry.
 */
public interface TelemetryRuntimeResolver {

    /**
     * Resolves the runtime and returns internal install telemetry.
     *
     * @param runtimeSource runtime source
     * @param postgresqlVersion requested PostgreSQL version
     * @return resolved runtime plus install telemetry
     */
    ResolvedRuntime resolveWithTelemetry(RuntimeSource runtimeSource, String postgresqlVersion);

    /**
     * Resolves the runtime and returns internal install telemetry, emitting fine-grained
     * resolve/download/verify/extract progress to the supplied listener.
     *
     * <p>The default implementation ignores the listener and delegates to
     * {@link #resolveWithTelemetry(RuntimeSource, String)}; archive-backed resolvers override this to
     * report download and extraction progress.
     *
     * @param runtimeSource runtime source
     * @param postgresqlVersion requested PostgreSQL version
     * @param progress startup progress listener
     * @return resolved runtime plus install telemetry
     */
    default ResolvedRuntime resolveWithTelemetry(
            final RuntimeSource runtimeSource,
            final String postgresqlVersion,
            final ManagedPostgresProgressListener progress) {
        return resolveWithTelemetry(runtimeSource, postgresqlVersion);
    }
}
