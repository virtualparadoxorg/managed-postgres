package eu.virtualparadox.managedpostgres.lifecycle.log;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.logging.PostgresLogs;
import eu.virtualparadox.managedpostgres.observe.PostgresLogLevel;
import eu.virtualparadox.managedpostgres.observe.PostgresLogLine;
import eu.virtualparadox.managedpostgres.observe.PostgresLogListener;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class PostgresLogBridgeSupportTest {

    @TempDir
    private Path temporaryDirectory;

    PostgresLogBridgeSupportTest() {}

    @Test
    void startReturnsNoOpCloseActionWhenNoDestinationActive() {
        final PostgresLogBridgeSupport support = new PostgresLogBridgeSupport();

        final Runnable closeAction = support.start(
                PostgresLogs.defaults(),
                temporaryDirectory.resolve("postgres.log"),
                Credentials.of("postgres", Secret.of("cluster-secret")),
                ClusterBootstrap.defaultCluster(),
                PostgresLogListener.none());

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
                ClusterBootstrap.defaultCluster().password(Secret.of("owner-secret")),
                PostgresLogListener.none());

        closeAction.run();

        assertThat(closeAction).isNotNull();
    }

    @Test
    void startTailsAndDeliversStructuredLinesWhenListenerActiveAndSlf4jDisabled() throws IOException {
        final PostgresLogBridgeSupport support = new PostgresLogBridgeSupport();
        final Path logFile = temporaryDirectory.resolve("postgres.log");
        Files.writeString(logFile, "");
        final List<PostgresLogLine> received = new CopyOnWriteArrayList<>();

        final Runnable closeAction = support.start(
                PostgresLogs.defaults(),
                logFile,
                Credentials.of("postgres", Secret.of("cluster-secret")),
                ClusterBootstrap.defaultCluster(),
                received::add);
        try {
            appendLine(logFile, "2026-06-05 10:00:00.000 UTC [1234] LOG:  database system is ready\n");

            awaitAtLeastOneLine(received);
            assertThat(received.get(0).level()).isEqualTo(PostgresLogLevel.LOG);
            assertThat(received.get(0).message()).contains("database system is ready");
        } finally {
            closeAction.run();
        }
    }

    @Test
    void startRedactsSecretsBeforeDeliveringToListener() throws IOException {
        final PostgresLogBridgeSupport support = new PostgresLogBridgeSupport();
        final Path logFile = temporaryDirectory.resolve("postgres.log");
        Files.writeString(logFile, "");
        final List<PostgresLogLine> received = new CopyOnWriteArrayList<>();

        final Runnable closeAction = support.start(
                PostgresLogs.defaults(),
                logFile,
                Credentials.of("postgres", Secret.of("cluster-secret")),
                ClusterBootstrap.defaultCluster(),
                received::add);
        try {
            appendLine(logFile, "2026-06-05 10:00:00.000 UTC [1234] LOG:  password is cluster-secret here\n");

            awaitAtLeastOneLine(received);
            assertThat(received.get(0).message()).doesNotContain("cluster-secret");
            assertThat(received.get(0).message()).contains("<redacted>");
        } finally {
            closeAction.run();
        }
    }

    @Test
    void startDeliversToBothSlf4jAndListenerWhenBothActive() throws IOException {
        final PostgresLogBridgeSupport support = new PostgresLogBridgeSupport();
        final Path logFile = temporaryDirectory.resolve("postgres.log");
        Files.writeString(logFile, "");
        final List<PostgresLogLine> received = new CopyOnWriteArrayList<>();

        final Runnable closeAction = support.start(
                PostgresLogs.defaults().toSlf4j().loggerName("managed.postgres.test"),
                logFile,
                Credentials.of("postgres", Secret.of("cluster-secret")),
                ClusterBootstrap.defaultCluster(),
                received::add);
        try {
            appendLine(logFile, "2026-06-05 10:00:00.000 UTC [1234] WARNING:  high water mark\n");

            awaitAtLeastOneLine(received);
            assertThat(received.get(0).level()).isEqualTo(PostgresLogLevel.WARNING);
        } finally {
            closeAction.run();
        }
    }

    @Test
    void wrapReturnsOriginalHandleWhenNoDestinationActive() {
        final PostgresLogBridgeSupport support = new PostgresLogBridgeSupport();
        final RecordingRunningPostgres handle = new RecordingRunningPostgres();
        try (RunningPostgres wrapped =
                support.wrap(handle, () -> {}, PostgresLogs.defaults(), PostgresLogListener.none())) {

            assertThat(wrapped).isSameAs(handle);
        }
    }

    @Test
    void wrapDecoratesHandleWhenListenerActiveAndSlf4jDisabled() {
        final PostgresLogBridgeSupport support = new PostgresLogBridgeSupport();
        final RecordingRunningPostgres handle = new RecordingRunningPostgres();
        final PostgresLogListener listener = line -> {
            // Intentionally ignores the line; only listener activity matters here.
        };
        try (RunningPostgres wrapped = support.wrap(handle, () -> {}, PostgresLogs.defaults(), listener)) {

            assertThat(wrapped).isNotSameAs(handle);
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
                PostgresLogs.defaults().toSlf4j(),
                PostgresLogListener.none())) {

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
                PostgresLogs.defaults().toSlf4j(),
                PostgresLogListener.none())) {
            assertThat(wrapped).isNotSameAs(closingHandle);
        }
        assertThat(closingHandle.closeCalls()).isEqualTo(1);
        assertThat(closeCalls.get()).isEqualTo(1);
    }

    private static void appendLine(final Path logFile, final String line) throws IOException {
        Files.writeString(logFile, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private static void awaitAtLeastOneLine(final List<PostgresLogLine> received) {
        final long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (received.isEmpty() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(Duration.ofMillis(25).toNanos());
        }
        assertThat(received).isNotEmpty();
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
