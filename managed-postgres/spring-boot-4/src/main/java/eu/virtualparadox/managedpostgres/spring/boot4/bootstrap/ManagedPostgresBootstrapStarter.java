package eu.virtualparadox.managedpostgres.spring.boot4.bootstrap;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Starts managed PostgreSQL during Spring Boot bootstrap and captures startup timing metrics.
 */
final class ManagedPostgresBootstrapStarter {

    ManagedPostgresBootstrapStarter() {}

    /**
     * Starts managed PostgreSQL and returns the populated bootstrap context.
     *
     * @param managedPostgres managed PostgreSQL lifecycle object
     * @return populated bootstrap context
     */
    ManagedPostgresBootstrapContext start(final ManagedPostgres managedPostgres) {
        final ManagedPostgres checkedManagedPostgres = Objects.requireNonNull(managedPostgres, "managedPostgres");
        final long startupStartedAt = System.nanoTime();
        final RunningPostgres runningPostgres =
                Objects.requireNonNull(checkedManagedPostgres.start(), "runningPostgres");
        final StartupTelemetryAccess startupTelemetry = startupTelemetry(runningPostgres);

        return ManagedPostgresBootstrapContext.of(
                checkedManagedPostgres,
                runningPostgres,
                metricsSince(
                        startupStartedAt, installDuration(startupTelemetry), healthcheckFailures(startupTelemetry)));
    }

    private static ManagedPostgresBootstrapMetrics metricsSince(
            final long startupStartedAt, final Duration installDuration, final int healthcheckFailures) {
        return new ManagedPostgresBootstrapMetrics(
                Duration.ofNanos(System.nanoTime() - startupStartedAt), installDuration, healthcheckFailures);
    }

    private static Duration installDuration(final StartupTelemetryAccess startupTelemetryAccess) {
        return telemetryValue(startupTelemetryAccess, "runtimeInstallDuration", Duration.class, Duration.ZERO);
    }

    private static int healthcheckFailures(final StartupTelemetryAccess startupTelemetryAccess) {
        return telemetryValue(startupTelemetryAccess, "healthcheckFailures", Integer.class, Integer.valueOf(0));
    }

    private static <T> T telemetryValue(
            final StartupTelemetryAccess startupTelemetryAccess,
            final String accessorName,
            final Class<T> valueType,
            final T fallback) {
        T telemetryValue = fallback;
        if (startupTelemetryAccess.method().isPresent()) {
            try {
                final Object startupTelemetry =
                        startupTelemetryAccess.telemetry().orElseThrow();
                telemetryValue = valueType.cast(
                        startupTelemetry.getClass().getMethod(accessorName).invoke(startupTelemetry));
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
                throw new IllegalStateException(
                        "Unable to read managed PostgreSQL startup telemetry details", exception);
            }
        }
        return telemetryValue;
    }

    private static StartupTelemetryAccess startupTelemetry(final RunningPostgres runningPostgres) {
        Optional<Method> startupTelemetryMethod = Optional.empty();
        boolean methodPresent = true;
        try {
            startupTelemetryMethod = Optional.of(runningPostgres.getClass().getDeclaredMethod("startupTelemetry"));
        } catch (NoSuchMethodException exception) {
            methodPresent = false;
        }
        final Optional<Method> resolvedMethod = methodPresent ? startupTelemetryMethod : Optional.empty();
        final Optional<Method> method = Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        final Optional<Object> telemetry;
        if (method.isPresent()) {
            try {
                telemetry = Optional.ofNullable(method.orElseThrow().invoke(runningPostgres));
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalStateException("Unable to read managed PostgreSQL startup telemetry", exception);
            }
        } else {
            telemetry = Optional.empty();
        }
        return new StartupTelemetryAccess(method, telemetry);
    }

    private record StartupTelemetryAccess(Optional<Method> method, Optional<Object> telemetry) {}
}
