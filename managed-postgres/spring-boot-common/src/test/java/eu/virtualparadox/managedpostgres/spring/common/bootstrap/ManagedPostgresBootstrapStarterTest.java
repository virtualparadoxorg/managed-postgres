package eu.virtualparadox.managedpostgres.spring.common.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ManagedPostgresBootstrapStarterTest {

    ManagedPostgresBootstrapStarterTest() {}

    @Test
    void starterFallsBackToZeroHealthcheckFailuresWhenHandleDoesNotExposeTelemetry() {
        final ManagedPostgresBootstrapContext bootstrapContext =
                new ManagedPostgresBootstrapStarter().start(new FixedManagedPostgres(new SimpleRunningPostgres()));

        assertThat(bootstrapContext.metrics().orElseThrow().installDuration()).isZero();
        assertThat(bootstrapContext.metrics().orElseThrow().healthcheckFailures())
                .isZero();
    }

    @Test
    void starterCapturesHealthcheckFailuresWhenHandleExposesTelemetry() {
        try (TelemetryAwareRunningPostgres runningPostgres =
                new TelemetryAwareRunningPostgres(Duration.ofMillis(125), 4)) {
            final ManagedPostgresBootstrapContext bootstrapContext =
                    new ManagedPostgresBootstrapStarter().start(new FixedManagedPostgres(runningPostgres));

            assertThat(runningPostgres.startupTelemetry().runtimeInstallDuration())
                    .isEqualTo(Duration.ofMillis(125));
            assertThat(runningPostgres.startupTelemetry().healthcheckFailures()).isEqualTo(4);
            assertThat(bootstrapContext.metrics().orElseThrow().installDuration())
                    .isEqualTo(Duration.ofMillis(125));
            assertThat(bootstrapContext.metrics().orElseThrow().healthcheckFailures())
                    .isEqualTo(4);
        }
    }

    @Test
    void starterReadsTelemetrySnapshotOnlyOnce() {
        try (TelemetryAwareRunningPostgres runningPostgres =
                new TelemetryAwareRunningPostgres(Duration.ofMillis(125), 4)) {
            final ManagedPostgresBootstrapContext bootstrapContext =
                    new ManagedPostgresBootstrapStarter().start(new FixedManagedPostgres(runningPostgres));

            assertThat(bootstrapContext.metrics().orElseThrow().installDuration())
                    .isEqualTo(Duration.ofMillis(125));
            assertThat(bootstrapContext.metrics().orElseThrow().healthcheckFailures())
                    .isEqualTo(4);
            assertThat(runningPostgres.startupTelemetryCalls()).isEqualTo(1);
        }
    }

    private record FixedManagedPostgres(RunningPostgres runningPostgres) implements ManagedPostgres {

        @Override
        public RunningPostgres start() {
            return runningPostgres;
        }

        @Override
        public PostgresStatus status() {
            return PostgresStatus.RUNNING;
        }

        @Override
        public DoctorReport doctor() {
            return new DoctorReport(PostgresStatus.RUNNING, List.of());
        }

        @Override
        public void stop() {
            // No-op test double.
        }

        @Override
        public void cleanup() {
            // No-op test double.
        }

        @Override
        public void destroyCluster() {
            // No-op test double.
        }

        @Override
        public void close() {
            // No-op test double.
        }
    }

    private static class SimpleRunningPostgres implements RunningPostgres {

        @Override
        public PostgresConnectionInfo connectionInfo() {
            return defaultConnectionInfo();
        }

        @Override
        public PostgresStatus status() {
            return PostgresStatus.RUNNING;
        }

        @Override
        public void backupTo(final Path target) {
            // No-op test double.
        }

        @Override
        public void restoreFrom(final Path backup, final RestoreOptions options) {
            // No-op test double.
        }

        @Override
        public void stop() {
            // No-op test double.
        }

        @Override
        public void close() {
            // No-op test double.
        }
    }

    static final class TelemetryAwareRunningPostgres extends SimpleRunningPostgres {

        private final Duration runtimeInstallDuration;
        private final int healthcheckFailures;
        private int startupTelemetryCalls;

        private TelemetryAwareRunningPostgres(final Duration runtimeInstallDuration, final int healthcheckFailures) {
            this.runtimeInstallDuration = runtimeInstallDuration;
            this.healthcheckFailures = healthcheckFailures;
        }

        StartupTelemetrySnapshot startupTelemetry() {
            startupTelemetryCalls++;
            return new StartupTelemetrySnapshot(runtimeInstallDuration, healthcheckFailures);
        }

        private int startupTelemetryCalls() {
            return startupTelemetryCalls;
        }
    }

    private record StartupTelemetrySnapshot(Duration runtimeInstallDuration, int healthcheckFailures) {}

    private static PostgresConnectionInfo defaultConnectionInfo() {
        return new PostgresConnectionInfo("127.0.0.1", 15432, "app", "app", Secret.redacted());
    }
}
