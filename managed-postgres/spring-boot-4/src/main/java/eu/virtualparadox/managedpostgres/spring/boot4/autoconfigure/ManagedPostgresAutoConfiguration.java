package eu.virtualparadox.managedpostgres.spring.boot4.autoconfigure;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.spring.boot4.health.ManagedPostgresHealthIndicator;
import eu.virtualparadox.managedpostgres.spring.common.bootstrap.ManagedPostgresBootstrapContext;
import eu.virtualparadox.managedpostgres.spring.common.bootstrap.ManagedPostgresBootstrapMetrics;
import eu.virtualparadox.managedpostgres.spring.common.config.ManagedPostgresSpringException;
import eu.virtualparadox.managedpostgres.spring.common.metrics.ManagedPostgresMeterBinder;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot 4 auto-configuration exposing managed PostgreSQL bootstrap handles as beans.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "managed-postgres", name = "enabled", havingValue = "true")
public final class ManagedPostgresAutoConfiguration {

    /**
     * Creates the auto-configuration.
     */
    public ManagedPostgresAutoConfiguration() {}

    /**
     * Exposes the bootstrap context for this Spring application context.
     *
     * @return context-specific managed PostgreSQL bootstrap handles
     */
    @Bean(name = ManagedPostgresBootstrapContext.BEAN_NAME)
    @ConditionalOnMissingBean(ManagedPostgresBootstrapContext.class)
    public ManagedPostgresBootstrapContext managedPostgresBootstrapContext() {
        return ManagedPostgresBootstrapContext.current();
    }

    /**
     * Exposes the managed PostgreSQL lifecycle object started during environment post-processing.
     *
     * @param bootstrapContext context-specific managed PostgreSQL bootstrap handles
     * @return managed PostgreSQL lifecycle object
     */
    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean
    public ManagedPostgres managedPostgres(final ManagedPostgresBootstrapContext bootstrapContext) {
        return require(
                bootstrapContext.managedPostgres(),
                "Managed PostgreSQL was not started during Spring Boot environment processing");
    }

    /**
     * Exposes the running PostgreSQL handle started during environment post-processing.
     *
     * @param bootstrapContext context-specific managed PostgreSQL bootstrap handles
     * @return running PostgreSQL handle
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RunningPostgres runningPostgres(final ManagedPostgresBootstrapContext bootstrapContext) {
        return require(
                bootstrapContext.runningPostgres(),
                "Running PostgreSQL handle is missing from Spring Boot bootstrap context");
    }

    /**
     * Exposes PostgreSQL connection information from the running handle.
     *
     * @param runningPostgres running PostgreSQL handle
     * @return PostgreSQL connection information
     */
    @Bean
    @ConditionalOnMissingBean
    public PostgresConnectionInfo postgresConnectionInfo(final RunningPostgres runningPostgres) {
        return runningPostgres.connectionInfo();
    }

    /**
     * Exposes a Spring Boot health indicator for the managed PostgreSQL handle.
     *
     * @param runningPostgres running PostgreSQL handle
     * @return managed PostgreSQL health indicator
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnMissingBean(name = "managedPostgresHealthIndicator")
    public ManagedPostgresHealthIndicator managedPostgresHealthIndicator(final RunningPostgres runningPostgres) {
        return new ManagedPostgresHealthIndicator(runningPostgres);
    }

    /**
     * Exposes optional Micrometer gauges for managed PostgreSQL lifecycle state.
     *
     * @param bootstrapContext bootstrap context carrying managed PostgreSQL handles and metrics
     * @param runningPostgres running PostgreSQL handle
     * @param applicationContext application context exposing the Micrometer meter registry
     * @return managed PostgreSQL meter binder
     */
    @Bean
    @ConditionalOnClass(name = {"io.micrometer.core.instrument.MeterRegistry", "io.micrometer.core.instrument.Gauge"})
    @ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
    @ConditionalOnProperty(prefix = "managed-postgres.metrics", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "managedPostgresMeterBinder")
    public ManagedPostgresMeterBinder managedPostgresMeterBinder(
            final ManagedPostgresBootstrapContext bootstrapContext,
            final RunningPostgres runningPostgres,
            final ApplicationContext applicationContext) {
        final Object meterRegistry = applicationContext.getBean("meterRegistry");
        final ManagedPostgresBootstrapMetrics metrics = bootstrapContext
                .metrics()
                .orElseGet(() -> new ManagedPostgresBootstrapMetrics(Duration.ZERO, Duration.ZERO, 0));
        final ManagedPostgresMeterBinder meterBinder = new ManagedPostgresMeterBinder(runningPostgres, metrics);
        meterBinder.bindTo(meterRegistry);
        return meterBinder;
    }

    private static <T> T require(final Optional<T> value, final String message) {
        return value.orElseThrow(() -> new ManagedPostgresSpringException(message));
    }
}
