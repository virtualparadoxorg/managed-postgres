package eu.virtualparadox.managedpostgres.spring.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.security.Secret;
import eu.virtualparadox.managedpostgres.spring.boot3.autoconfigure.ManagedPostgresAutoConfiguration;
import eu.virtualparadox.managedpostgres.spring.common.bootstrap.ManagedPostgresBootstrapContextTestSupport;
import eu.virtualparadox.managedpostgres.spring.common.bootstrap.ManagedPostgresBootstrapMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

public final class ManagedPostgresMetricsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ManagedPostgresAutoConfiguration.class))
            .withUserConfiguration(SimpleMeterRegistryConfiguration.class);

    ManagedPostgresMetricsAutoConfigurationTest() {}

    @AfterEach
    void resetBootstrapContext() {
        ManagedPostgresBootstrapContextTestSupport.reset();
    }

    @Test
    void autoConfigurationExposesManagedPostgresMeterBinderWhenMetricsAreEnabled() {
        final BootstrapFixture fixture = BootstrapFixture.create(PostgresStatus.RUNNING);
        ManagedPostgresBootstrapContextTestSupport.store(
                fixture.postgres(),
                fixture.runningPostgres(),
                new ManagedPostgresBootstrapMetrics(Duration.ofMillis(2_500), Duration.ofMillis(500), 4));

        contextRunner
                .withPropertyValues("managed-postgres.enabled=true", "managed-postgres.metrics.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ManagedPostgresMeterBinder.class);
                    assertThat(context).hasSingleBean(MeterRegistry.class);
                    assertThat(context.getBean(MeterRegistry.class)
                                    .get("managed.postgres.running")
                                    .gauge()
                                    .value())
                            .isEqualTo(1.0d);
                    assertThat(context.getBean(MeterRegistry.class)
                                    .get("managed.postgres.healthy")
                                    .gauge()
                                    .value())
                            .isEqualTo(1.0d);
                    assertThat(context.getBean(MeterRegistry.class)
                                    .get("managed.postgres.port")
                                    .gauge()
                                    .value())
                            .isEqualTo(15_432.0d);
                    assertThat(context.getBean(MeterRegistry.class)
                                    .get("managed.postgres.startup.duration")
                                    .gauge()
                                    .value())
                            .isEqualTo(2.5d);
                    assertThat(context.getBean(MeterRegistry.class)
                                    .get("managed.postgres.install.duration")
                                    .gauge()
                                    .value())
                            .isEqualTo(0.5d);
                    assertThat(context.getBean(MeterRegistry.class)
                                    .get("managed.postgres.healthcheck.failures")
                                    .gauge()
                                    .value())
                            .isEqualTo(4.0d);
                });
    }

    @Test
    void autoConfigurationSkipsManagedPostgresMeterBinderWhenMetricsAreDisabled() {
        final BootstrapFixture fixture = BootstrapFixture.create(PostgresStatus.RUNNING);
        ManagedPostgresBootstrapContextTestSupport.store(
                fixture.postgres(),
                fixture.runningPostgres(),
                new ManagedPostgresBootstrapMetrics(Duration.ofSeconds(1), Duration.ZERO, 0));

        contextRunner
                .withPropertyValues("managed-postgres.enabled=true", "managed-postgres.metrics.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ManagedPostgresMeterBinder.class));
    }

    @Test
    void autoConfigurationSkipsManagedPostgresMeterBinderWhenMetricsPropertyIsAbsent() {
        final BootstrapFixture fixture = BootstrapFixture.create(PostgresStatus.RUNNING);
        ManagedPostgresBootstrapContextTestSupport.store(
                fixture.postgres(),
                fixture.runningPostgres(),
                new ManagedPostgresBootstrapMetrics(Duration.ofSeconds(1), Duration.ZERO, 0));

        contextRunner.withPropertyValues("managed-postgres.enabled=true").run(context -> assertThat(context)
                .doesNotHaveBean(ManagedPostgresMeterBinder.class));
    }

    private static final class SimpleMeterRegistryConfiguration {

        private SimpleMeterRegistryConfiguration() {}

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private static final class BootstrapFixture {

        private final ManagedPostgres postgres = mock(ManagedPostgres.class);
        private final RunningPostgres runningPostgres = mock(RunningPostgres.class);
        private final PostgresConnectionInfo connectionInfo =
                new PostgresConnectionInfo("127.0.0.1", 15_432, "app", "app", Secret.of("metrics-boot-secret"));

        private BootstrapFixture(final PostgresStatus status) {
            when(runningPostgres.status()).thenReturn(status);
            when(runningPostgres.connectionInfo()).thenReturn(connectionInfo);
        }

        private static BootstrapFixture create(final PostgresStatus status) {
            return new BootstrapFixture(status);
        }

        private ManagedPostgres postgres() {
            return postgres;
        }

        private RunningPostgres runningPostgres() {
            return runningPostgres;
        }
    }
}
