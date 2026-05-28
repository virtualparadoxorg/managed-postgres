package eu.virtualparadox.managedpostgres.spring.boot4.bootstrap;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable bootstrap metrics captured while managed PostgreSQL starts during Spring Boot environment processing.
 *
 * @param startupDuration total managed PostgreSQL startup duration observed by the Spring bootstrap layer
 * @param installDuration time spent installing a new runtime during startup, or zero when no install occurred
 * @param healthcheckFailures number of unhealthy readiness polls observed before startup succeeded
 */
public record ManagedPostgresBootstrapMetrics(
        Duration startupDuration,
        Duration installDuration,
        int healthcheckFailures) {

    /**
     * Creates immutable bootstrap metrics.
     *
     * @param startupDuration total managed PostgreSQL startup duration observed by the Spring bootstrap layer
     * @param installDuration time spent installing a new runtime during startup, or zero when no install occurred
     * @param healthcheckFailures number of unhealthy readiness polls observed before startup succeeded
     */
    public ManagedPostgresBootstrapMetrics {
        final Duration checkedStartupDuration = Objects.requireNonNull(startupDuration, "startupDuration");
        final Duration checkedInstallDuration = Objects.requireNonNull(installDuration, "installDuration");
        if (checkedStartupDuration.isNegative()) {
            throw new IllegalArgumentException("startupDuration must not be negative");
        }
        if (checkedInstallDuration.isNegative()) {
            throw new IllegalArgumentException("installDuration must not be negative");
        }
        if (healthcheckFailures < 0) {
            throw new IllegalArgumentException("healthcheckFailures must not be negative");
        }
        startupDuration = checkedStartupDuration;
        installDuration = checkedInstallDuration;
    }
}
