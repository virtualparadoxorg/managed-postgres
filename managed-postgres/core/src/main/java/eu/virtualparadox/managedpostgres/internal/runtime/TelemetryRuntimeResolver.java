package eu.virtualparadox.managedpostgres.internal.runtime;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;

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
}
