package eu.virtualparadox.managedpostgres.spring.boot4.metrics;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.spring.boot4.bootstrap.ManagedPostgresBootstrapMetrics;
import eu.virtualparadox.managedpostgres.spring.boot4.config.ManagedPostgresSpringException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

/**
 * Reflection-based optional Micrometer meter binder for managed PostgreSQL lifecycle state.
 */
public final class ManagedPostgresMeterBinder {

    private static final String GAUGE_CLASS_NAME = "io.micrometer.core.instrument.Gauge";
    private static final String REGISTRY_CLASS_NAME = "io.micrometer.core.instrument.MeterRegistry";

    private final RunningPostgres runningPostgres;
    private final ManagedPostgresBootstrapMetrics metrics;

    /**
     * Creates the meter binder.
     *
     * @param runningPostgres running PostgreSQL handle
     * @param metrics bootstrap timing metrics
     */
    public ManagedPostgresMeterBinder(
            final RunningPostgres runningPostgres,
            final ManagedPostgresBootstrapMetrics metrics) {
        this.runningPostgres = Objects.requireNonNull(runningPostgres, "runningPostgres");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Binds managed PostgreSQL gauges to the supplied registry.
     *
     * @param registry meter registry
     */
    public void bindTo(final Object registry) {
        final Object checkedRegistry = Objects.requireNonNull(registry, "registry");
        registerGauge(
                "managed.postgres.running",
                "Whether managed PostgreSQL is currently running",
                ManagedPostgresMeterBinder::runningValue,
                checkedRegistry);
        registerGauge(
                "managed.postgres.healthy",
                "Whether managed PostgreSQL currently reports a healthy running status",
                ManagedPostgresMeterBinder::healthyValue,
                checkedRegistry);
        registerGauge(
                "managed.postgres.port",
                "The currently selected managed PostgreSQL port",
                ManagedPostgresMeterBinder::portValue,
                checkedRegistry);
        registerGauge(
                "managed.postgres.startup.duration",
                "The managed PostgreSQL startup duration observed during Spring Boot bootstrap, in seconds",
                ignoredRunningPostgres -> startupDurationSeconds(),
                checkedRegistry);
        registerGauge(
                "managed.postgres.install.duration",
                "The time spent installing a new PostgreSQL runtime during startup, in seconds",
                ignoredRunningPostgres -> installDurationSeconds(),
                checkedRegistry);
        registerGauge(
                "managed.postgres.healthcheck.failures",
                "The number of unhealthy readiness polls observed before managed PostgreSQL startup succeeded",
                ignoredRunningPostgres -> healthcheckFailures(),
                checkedRegistry);
    }

    private void registerGauge(
            final String name,
            final String description,
            final ToDoubleFunction<RunningPostgres> valueFunction,
            final Object registry) {
        try {
            final Class<?> gaugeClass = Class.forName(GAUGE_CLASS_NAME);
            final Class<?> registryClass = Class.forName(REGISTRY_CLASS_NAME);
            final Method builderMethod = gaugeClass.getMethod(
                    "builder",
                    String.class,
                    Object.class,
                    ToDoubleFunction.class);
            final Object builder = builderMethod.invoke(null, name, runningPostgres, valueFunction);
            final Object describedBuilder = builder.getClass()
                    .getMethod("description", String.class)
                    .invoke(builder, description);
            describedBuilder.getClass().getMethod("register", registryClass).invoke(describedBuilder, registry);
        } catch (ClassNotFoundException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException exception) {
            throw new ManagedPostgresSpringException("Unable to register managed PostgreSQL Micrometer gauges", exception);
        }
    }

    private static double runningValue(final RunningPostgres runningPostgres) {
        return runningPostgres.status() == PostgresStatus.RUNNING ? 1.0d : 0.0d;
    }

    private static double healthyValue(final RunningPostgres runningPostgres) {
        return runningPostgres.status() == PostgresStatus.RUNNING ? 1.0d : 0.0d;
    }

    private static double portValue(final RunningPostgres runningPostgres) {
        return runningPostgres.connectionInfo().port();
    }

    private double startupDurationSeconds() {
        return metrics.startupDuration().toNanos() / 1_000_000_000.0d;
    }

    private double installDurationSeconds() {
        return metrics.installDuration().toNanos() / 1_000_000_000.0d;
    }

    private double healthcheckFailures() {
        return metrics.healthcheckFailures();
    }
}
