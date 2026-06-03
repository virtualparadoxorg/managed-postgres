package eu.virtualparadox.managedpostgres.spring.boot4.bootstrap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

final class ManagedPostgresBootstrapMetricsTest {

    ManagedPostgresBootstrapMetricsTest() {}

    @Test
    void bootstrapMetricsRejectNegativeStartupDuration() {
        assertThatThrownBy(() -> new ManagedPostgresBootstrapMetrics(Duration.ofSeconds(-1), Duration.ZERO, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startupDuration");
    }

    @Test
    void bootstrapMetricsRejectNegativeInstallDuration() {
        assertThatThrownBy(() -> new ManagedPostgresBootstrapMetrics(Duration.ZERO, Duration.ofSeconds(-1), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("installDuration");
    }

    @Test
    void bootstrapMetricsRejectNegativeHealthcheckFailures() {
        assertThatThrownBy(() -> new ManagedPostgresBootstrapMetrics(Duration.ZERO, Duration.ZERO, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("healthcheckFailures");
    }
}
