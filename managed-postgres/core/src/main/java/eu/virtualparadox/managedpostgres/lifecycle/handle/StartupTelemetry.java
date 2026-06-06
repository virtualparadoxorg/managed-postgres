package eu.virtualparadox.managedpostgres.lifecycle.handle;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable startup telemetry captured while PostgreSQL becomes ready.
 *
 * @param runtimeInstallDuration time spent publishing a runtime into cache during startup, or zero when no install occurred
 * @param healthcheckFailures number of unhealthy readiness polls observed before startup succeeded
 */
public record StartupTelemetry(Duration runtimeInstallDuration, int healthcheckFailures) {

    /**
     * Creates immutable startup telemetry.
     *
     * @param runtimeInstallDuration time spent publishing a runtime into cache during startup, or zero when no install occurred
     * @param healthcheckFailures number of unhealthy readiness polls observed before startup succeeded
     */
    public StartupTelemetry {
        final Duration checkedRuntimeInstallDuration =
                Objects.requireNonNull(runtimeInstallDuration, "runtimeInstallDuration");
        if (checkedRuntimeInstallDuration.isNegative()) {
            throw new IllegalArgumentException("runtimeInstallDuration must not be negative");
        }
        if (healthcheckFailures < 0) {
            throw new IllegalArgumentException("healthcheckFailures must not be negative");
        }
        runtimeInstallDuration = checkedRuntimeInstallDuration;
    }
}
