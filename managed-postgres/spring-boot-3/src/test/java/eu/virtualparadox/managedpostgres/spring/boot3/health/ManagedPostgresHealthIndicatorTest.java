package eu.virtualparadox.managedpostgres.spring.boot3.health;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

public final class ManagedPostgresHealthIndicatorTest {

    private static final String RAW_PASSWORD = "health-secret";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ManagedPostgresAutoConfiguration.class));

    ManagedPostgresHealthIndicatorTest() {}

    @AfterEach
    void resetBootstrapContext() {
        ManagedPostgresBootstrapContextTestSupport.reset();
    }

    @Test
    void runningStatusMapsToUp() {
        final Health health = healthFor(PostgresStatus.RUNNING);

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void stoppedStatusMapsToOutOfService() {
        final Health health = healthFor(PostgresStatus.STOPPED);

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
    }

    @Test
    void failedStatusMapsToDown() {
        final Health health = healthFor(PostgresStatus.FAILED);

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void healthDetailsExposeConnectionIdentityWithoutSecrets() {
        final Health health = healthFor(PostgresStatus.RUNNING);

        assertThat(health.getDetails())
                .containsEntry("status", PostgresStatus.RUNNING.name())
                .containsEntry("host", "127.0.0.1")
                .containsEntry("port", 15432)
                .containsEntry("database", "app")
                .containsEntry("username", "app");
        assertThat(health.getDetails()).doesNotContainKeys("password", "secret", "diagnostics", "command");
        assertThat(health.getDetails().toString())
                .doesNotContain(RAW_PASSWORD)
                .doesNotContain("Secret")
                .doesNotContain("PGPASSWORD")
                .doesNotContain("command")
                .doesNotContain("diagnostic");
    }

    @Test
    void autoConfigurationExposesManagedPostgresHealthIndicatorWhenEnabled() {
        final BootstrapFixture fixture = BootstrapFixture.create(PostgresStatus.RUNNING);
        ManagedPostgresBootstrapContextTestSupport.store(fixture.postgres(), fixture.runningPostgres());

        contextRunner.withPropertyValues("managed-postgres.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(ManagedPostgresHealthIndicator.class);
            assertThat(context).hasBean("managedPostgresHealthIndicator");
        });
    }

    @Test
    void autoConfigurationBacksOffWhenUserProvidesManagedPostgresHealthIndicator() {
        final BootstrapFixture fixture = BootstrapFixture.create(PostgresStatus.RUNNING);
        ManagedPostgresBootstrapContextTestSupport.store(fixture.postgres(), fixture.runningPostgres());

        contextRunner
                .withUserConfiguration(CustomHealthIndicatorConfiguration.class)
                .withPropertyValues("managed-postgres.enabled=true")
                .run(context -> assertThat(context.getBean("managedPostgresHealthIndicator"))
                        .isSameAs(context.getBean(CustomHealthIndicatorConfiguration.class)
                                .indicator()));
    }

    private static Health healthFor(final PostgresStatus status) {
        return new ManagedPostgresHealthIndicator(
                        BootstrapFixture.create(status).runningPostgres())
                .health();
    }

    private static final class CustomHealthIndicatorConfiguration {

        private final HealthIndicator indicator = () -> Health.up().build();

        private CustomHealthIndicatorConfiguration() {}

        @Bean
        HealthIndicator managedPostgresHealthIndicator() {
            return indicator;
        }

        private HealthIndicator indicator() {
            return indicator;
        }
    }

    private static final class BootstrapFixture {

        private final ManagedPostgres postgres = mock(ManagedPostgres.class);
        private final RunningPostgres runningPostgres = mock(RunningPostgres.class);
        private final PostgresConnectionInfo connectionInfo =
                new PostgresConnectionInfo("127.0.0.1", 15432, "app", "app", Secret.of(RAW_PASSWORD));

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
