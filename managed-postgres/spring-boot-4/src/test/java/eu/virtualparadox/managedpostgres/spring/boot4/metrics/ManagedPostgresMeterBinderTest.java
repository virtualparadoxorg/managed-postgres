package eu.virtualparadox.managedpostgres.spring.boot4.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.security.Secret;
import eu.virtualparadox.managedpostgres.spring.boot4.bootstrap.ManagedPostgresBootstrapMetrics;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public final class ManagedPostgresMeterBinderTest {

    private static final String RAW_PASSWORD = "metrics-secret";

    ManagedPostgresMeterBinderTest() {
    }

    @Test
    void runningStatusPublishesRunningHealthyAndPortGaugesWithoutTags() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final ManagedPostgresMeterBinder binder = new ManagedPostgresMeterBinder(
                runningPostgres(PostgresStatus.RUNNING),
                new ManagedPostgresBootstrapMetrics(Duration.ofMillis(1_250), Duration.ofMillis(250), 3));

        binder.bindTo(registry);

        assertThat(registry.get("managed.postgres.running").gauge().value()).isEqualTo(1.0d);
        assertThat(registry.get("managed.postgres.healthy").gauge().value()).isEqualTo(1.0d);
        assertThat(registry.get("managed.postgres.port").gauge().value()).isEqualTo(15_432.0d);
        assertThat(registry.get("managed.postgres.startup.duration").gauge().value()).isEqualTo(1.25d);
        assertThat(registry.get("managed.postgres.install.duration").gauge().value()).isEqualTo(0.25d);
        assertThat(registry.get("managed.postgres.healthcheck.failures").gauge().value()).isEqualTo(3.0d);
        assertThat(registry.getMeters()).extracting(Meter::getId).extracting(Meter.Id::getTags).allSatisfy(tags ->
                assertThat(tags).isEmpty());
    }

    @Test
    void failedStatusPublishesZeroForRunningAndHealthy() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final ManagedPostgresMeterBinder binder = new ManagedPostgresMeterBinder(
                runningPostgres(PostgresStatus.FAILED),
                new ManagedPostgresBootstrapMetrics(Duration.ZERO, Duration.ZERO, 0));

        binder.bindTo(registry);

        assertThat(registry.get("managed.postgres.running").gauge().value()).isZero();
        assertThat(registry.get("managed.postgres.healthy").gauge().value()).isZero();
        assertThat(registry.get("managed.postgres.port").gauge().value()).isEqualTo(15_432.0d);
        assertThat(registry.get("managed.postgres.startup.duration").gauge().value()).isZero();
        assertThat(registry.get("managed.postgres.install.duration").gauge().value()).isZero();
        assertThat(registry.get("managed.postgres.healthcheck.failures").gauge().value()).isZero();
        assertThat(registry.getMeters().toString())
                .doesNotContain(RAW_PASSWORD)
                .doesNotContain("127.0.0.1")
                .doesNotContain("jdbc")
                .doesNotContain("database")
                .doesNotContain("username");
    }

    private static RunningPostgres runningPostgres(final PostgresStatus status) {
        final RunningPostgres runningPostgres = mock(RunningPostgres.class);
        when(runningPostgres.status()).thenReturn(status);
        when(runningPostgres.connectionInfo()).thenReturn(new PostgresConnectionInfo(
                "127.0.0.1",
                15_432,
                "app",
                "app",
                Secret.of(RAW_PASSWORD)));
        return runningPostgres;
    }
}
