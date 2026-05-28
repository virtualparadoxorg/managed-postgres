package eu.virtualparadox.managedpostgres.lifecycle.log;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.logging.PostgresLogs;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class PostgresLogBridgeSupportTest {

    @TempDir
    private Path temporaryDirectory;

    PostgresLogBridgeSupportTest() {
    }

    @Test
    void startReturnsNoOpCloseActionWhenSlf4jBridgeDisabled() {
        final PostgresLogBridgeSupport support = new PostgresLogBridgeSupport();

        final Runnable closeAction = support.start(
                PostgresLogs.defaults(),
                temporaryDirectory.resolve("postgres.log"),
                Credentials.of("postgres", Secret.of("cluster-secret")),
                ClusterBootstrap.defaultCluster());

        closeAction.run();

        assertThat(closeAction).isNotNull();
    }

    @Test
    void startReturnsActiveCloseActionWhenSlf4jBridgeEnabled() throws IOException {
        final PostgresLogBridgeSupport support = new PostgresLogBridgeSupport();
        final Path logFile = temporaryDirectory.resolve("postgres.log");
        Files.writeString(logFile, "");

        final Runnable closeAction = support.start(
                PostgresLogs.defaults().toSlf4j().loggerName("managed.postgres.test"),
                logFile,
                Credentials.of("postgres", Secret.of("cluster-secret")),
                ClusterBootstrap.defaultCluster().password(Secret.of("owner-secret")));

        closeAction.run();

        assertThat(closeAction).isNotNull();
    }

    @Test
    void wrapReturnsOriginalHandleWhenSlf4jBridgeDisabled() {
        final PostgresLogBridgeSupport support = new PostgresLogBridgeSupport();
        final RecordingRunningPostgres handle = new RecordingRunningPostgres();
        try (RunningPostgres wrapped = support.wrap(handle, () -> { }, PostgresLogs.defaults())) {

            assertThat(wrapped).isSameAs(handle);
        }
    }

    @Test
    void wrapDecoratesHandleWhenSlf4jBridgeEnabledAndRunsCloseActionOnStopAndClose() {
        final PostgresLogBridgeSupport support = new PostgresLogBridgeSupport();
        final AtomicInteger stopCloseCalls = new AtomicInteger();
        final RecordingRunningPostgres handle = new RecordingRunningPostgres();
        try (RunningPostgres wrapped = support.wrap(
                handle,
                stopCloseCalls::incrementAndGet,
                PostgresLogs.defaults().toSlf4j())) {

            wrapped.stop();

            assertThat(wrapped).isNotSameAs(handle);
            assertThat(handle.stopCalls()).isEqualTo(1);
            assertThat(handle.closeCalls()).isEqualTo(0);
            assertThat(stopCloseCalls.get()).isEqualTo(1);
        }
        final AtomicInteger closeCalls = new AtomicInteger();
        final RecordingRunningPostgres closingHandle = new RecordingRunningPostgres();
        try (RunningPostgres wrapped = support.wrap(
                closingHandle,
                closeCalls::incrementAndGet,
                PostgresLogs.defaults().toSlf4j())) {
            assertThat(wrapped).isNotSameAs(closingHandle);
        }
        assertThat(closingHandle.closeCalls()).isEqualTo(1);
        assertThat(closeCalls.get()).isEqualTo(1);
    }

    private static final class RecordingRunningPostgres implements RunningPostgres {

        private final AtomicInteger stopCalls;
        private final AtomicInteger closeCalls;

        private RecordingRunningPostgres() {
            stopCalls = new AtomicInteger();
            closeCalls = new AtomicInteger();
        }

        private int stopCalls() {
            return stopCalls.get();
        }

        private int closeCalls() {
            return closeCalls.get();
        }

        @Override
        public PostgresConnectionInfo connectionInfo() {
            return new PostgresConnectionInfo("127.0.0.1", 15432, "postgres", "postgres", Secret.redacted());
        }

        @Override
        public PostgresStatus status() {
            return PostgresStatus.RUNNING;
        }

        @Override
        public void backupTo(final Path target) {
            assertThat(target).isNotNull();
        }

        @Override
        public void restoreFrom(final Path backup, final RestoreOptions options) {
            assertThat(backup).isNotNull();
            assertThat(options).isNotNull();
        }

        @Override
        public void stop() {
            stopCalls.incrementAndGet();
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
        }
    }
}
