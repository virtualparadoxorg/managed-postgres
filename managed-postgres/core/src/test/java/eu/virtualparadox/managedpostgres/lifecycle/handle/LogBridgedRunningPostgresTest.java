package eu.virtualparadox.managedpostgres.lifecycle.handle;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public final class LogBridgedRunningPostgresTest {

    LogBridgedRunningPostgresTest() {}

    @Test
    void startupTelemetryDelegatesWhenWrappedHandleExposesIt() {
        try (TelemetryAwareRunningPostgres delegate =
                        new TelemetryAwareRunningPostgres(new StartupTelemetry(Duration.ofMillis(25), 3));
                LogBridgedRunningPostgres handle = new LogBridgedRunningPostgres(delegate, () -> {
                    // No close side effect is needed for telemetry delegation coverage.
                })) {
            assertThat(delegate.startupTelemetry().runtimeInstallDuration()).isEqualTo(Duration.ofMillis(25));
            assertThat(delegate.startupTelemetry().healthcheckFailures()).isEqualTo(3);
            assertThat(handle.startupTelemetry().runtimeInstallDuration()).isEqualTo(Duration.ofMillis(25));
            assertThat(handle.startupTelemetry().healthcheckFailures()).isEqualTo(3);
        }
    }

    @Test
    void startupTelemetryFallsBackToZeroWhenWrappedHandleDoesNotExposeIt() {
        try (LogBridgedRunningPostgres handle = new LogBridgedRunningPostgres(new SimpleRunningPostgres(), () -> {
            // No close side effect is needed for telemetry fallback coverage.
        })) {
            assertThat(handle.startupTelemetry().runtimeInstallDuration()).isZero();
            assertThat(handle.startupTelemetry().healthcheckFailures()).isZero();
        }
    }

    @Test
    void closeRunsCloseActionAfterDelegating() {
        final AtomicInteger closeActionCalls = new AtomicInteger();
        final TelemetryAwareRunningPostgres delegate =
                new TelemetryAwareRunningPostgres(new StartupTelemetry(Duration.ZERO, 1));
        try (LogBridgedRunningPostgres handle =
                new LogBridgedRunningPostgres(delegate, closeActionCalls::incrementAndGet)) {
            assertThat(handle.status()).isEqualTo(PostgresStatus.RUNNING);
        }

        assertThat(delegate.closeCalls()).isEqualTo(1);
        assertThat(closeActionCalls.get()).isEqualTo(1);
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

    private static final class TelemetryAwareRunningPostgres extends SimpleRunningPostgres {

        private final StartupTelemetry startupTelemetry;
        private final AtomicInteger closeCalls = new AtomicInteger();

        private TelemetryAwareRunningPostgres(final StartupTelemetry startupTelemetry) {
            this.startupTelemetry = startupTelemetry;
        }

        StartupTelemetry startupTelemetry() {
            return startupTelemetry;
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
        }

        private int closeCalls() {
            return closeCalls.get();
        }
    }

    private static PostgresConnectionInfo defaultConnectionInfo() {
        return new PostgresConnectionInfo("127.0.0.1", 15432, "app", "app", Secret.redacted());
    }
}
