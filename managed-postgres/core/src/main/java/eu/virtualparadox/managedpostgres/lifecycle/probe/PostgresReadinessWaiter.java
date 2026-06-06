package eu.virtualparadox.managedpostgres.lifecycle.probe;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.lifecycle.PostgresStartupDiagnostics;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

/**
 * Polls PostgreSQL readiness until the configured startup timeout expires.
 */
public final class PostgresReadinessWaiter {

    private static final Duration READINESS_BACKOFF = Duration.ofMillis(25);

    private final CommandRunner commandRunner;
    private final Duration startupTimeout;

    /**
     * Creates a readiness waiter.
     *
     * @param commandRunner command runner
     * @param startupTimeout maximum startup readiness wait
     */
    public PostgresReadinessWaiter(final CommandRunner commandRunner, final Duration startupTimeout) {
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
        this.startupTimeout = Objects.requireNonNull(startupTimeout, "startupTimeout");
    }

    /**
     * Waits until {@code pg_isready} reports a healthy instance.
     *
     * @param runtimeDirectory PostgreSQL runtime directory
     * @param connectionInfo connection details
     * @param layout PostgreSQL layout
     * @return final healthy probe result
     */
    public ReadinessOutcome await(
            final Path runtimeDirectory, final PostgresConnectionInfo connectionInfo, final PostgresLayout layout) {
        final PgIsReadyProbe probe = new PgIsReadyProbe(runtimeDirectory, commandRunner);
        final long deadline = System.nanoTime() + startupTimeout.toNanos();
        PostgresProbeResult result = PostgresProbeResult.unhealthy(
                "pg_isready was not executed",
                PostgresStartupDiagnostics.diagnostic("pg_isready", Map.of("status", "not-executed")));
        boolean ready = false;
        int failedHealthcheckCount = 0;

        while (!ready && System.nanoTime() <= deadline) {
            result = probe.probe(connectionInfo, startupTimeout);
            ready = result.healthy();
            if (!ready) {
                failedHealthcheckCount++;
                LockSupport.parkNanos(READINESS_BACKOFF.toNanos());
            }
        }
        if (!ready) {
            throw PostgresStartupDiagnostics.startupTimeout(connectionInfo, layout, result);
        }

        return new ReadinessOutcome(result, failedHealthcheckCount);
    }

    /**
     * Immutable readiness polling outcome.
     *
     * @param finalResult final successful probe result
     * @param failedHealthcheckCount number of unhealthy readiness polls observed before startup succeeded
     */
    public record ReadinessOutcome(PostgresProbeResult finalResult, int failedHealthcheckCount) {

        /**
         * Validates a readiness outcome.
         *
         * @param finalResult final successful probe result
         * @param failedHealthcheckCount number of unhealthy readiness polls observed before startup succeeded
         */
        public ReadinessOutcome {
            Objects.requireNonNull(finalResult, "finalResult");
            if (failedHealthcheckCount < 0) {
                throw new IllegalArgumentException("failedHealthcheckCount must not be negative");
            }
        }
    }
}
