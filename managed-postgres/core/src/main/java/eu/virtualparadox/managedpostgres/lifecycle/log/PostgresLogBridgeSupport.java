package eu.virtualparadox.managedpostgres.lifecycle.log;

import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.logging.PostgresLogs;
import eu.virtualparadox.managedpostgres.lifecycle.handle.LogBridgedRunningPostgres;
import eu.virtualparadox.managedpostgres.observe.PostgresLogListener;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Starts and wraps optional PostgreSQL log bridges without leaking process details to the public API.
 */
public final class PostgresLogBridgeSupport {

    /**
     * Creates a PostgreSQL log bridge support helper.
     */
    public PostgresLogBridgeSupport() {}

    /**
     * Starts an optional log bridge for the supplied PostgreSQL log file.
     *
     * <p>The tailing bridge starts when SLF4J bridging is enabled, a structured listener is active, or both; the
     * resulting sink forwards each line to whichever destinations are active. When neither is active no tail thread is
     * started.
     *
     * @param logs PostgreSQL log handling configuration
     * @param logFile PostgreSQL log file
     * @param credentials PostgreSQL credentials
     * @param clusterBootstrap bootstrap credentials that may also need redaction
     * @param listener structured log listener, or {@link PostgresLogListener#none()} when no listener is configured
     * @return close action for the started bridge, or a no-op action when no destination is active
     */
    public Runnable start(
            final PostgresLogs logs,
            final Path logFile,
            final Credentials credentials,
            final ClusterBootstrap clusterBootstrap,
            final PostgresLogListener listener) {
        final PostgresLogs checkedLogs = Objects.requireNonNull(logs, "logs");
        final PostgresLogListener checkedListener = Objects.requireNonNull(listener, "listener");
        final Runnable closeAction;
        if (isActive(checkedLogs, checkedListener)) {
            closeAction = new BridgeCloseAction(new TailingPostgresLogBridge(
                    Objects.requireNonNull(logFile, "logFile"),
                    checkedLogs.loggerName(),
                    secrets(credentials, clusterBootstrap),
                    composeSink(checkedLogs, checkedListener)));
        } else {
            closeAction = () -> {
                // Intentionally empty: file-only logging remains the default behavior.
            };
        }

        return closeAction;
    }

    /**
     * Wraps a running PostgreSQL handle so the optional bridge closes with the handle.
     *
     * @param handle running PostgreSQL handle
     * @param closeAction bridge close action
     * @param logs PostgreSQL log handling configuration
     * @param listener structured log listener, or {@link PostgresLogListener#none()} when no listener is configured
     * @return wrapped handle when a bridge is active, otherwise the original handle
     */
    public RunningPostgres wrap(
            final RunningPostgres handle,
            final Runnable closeAction,
            final PostgresLogs logs,
            final PostgresLogListener listener) {
        final RunningPostgres checkedHandle = Objects.requireNonNull(handle, "handle");
        final Runnable checkedCloseAction = Objects.requireNonNull(closeAction, "closeAction");
        final PostgresLogs checkedLogs = Objects.requireNonNull(logs, "logs");
        final PostgresLogListener checkedListener = Objects.requireNonNull(listener, "listener");
        final RunningPostgres wrappedHandle;
        if (isActive(checkedLogs, checkedListener)) {
            wrappedHandle = new LogBridgedRunningPostgres(checkedHandle, checkedCloseAction);
        } else {
            wrappedHandle = checkedHandle;
        }

        return wrappedHandle;
    }

    private static boolean isActive(final PostgresLogs logs, final PostgresLogListener listener) {
        return logs.bridgeToSlf4j() || listener.isActive();
    }

    private static PostgresLogSink composeSink(final PostgresLogs logs, final PostgresLogListener listener) {
        final List<PostgresLogSink> sinks = new ArrayList<>();
        if (logs.bridgeToSlf4j()) {
            sinks.add(new Slf4jPostgresLogSink());
        }
        if (listener.isActive()) {
            sinks.add(new ListenerPostgresLogSink(listener));
        }
        final PostgresLogSink sink;
        if (sinks.size() == 1) {
            sink = sinks.get(0);
        } else {
            sink = new CompositePostgresLogSink(sinks);
        }

        return sink;
    }

    private static List<Secret> secrets(final Credentials credentials, final ClusterBootstrap clusterBootstrap) {
        final Credentials checkedCredentials = Objects.requireNonNull(credentials, "credentials");
        final ClusterBootstrap checkedClusterBootstrap = Objects.requireNonNull(clusterBootstrap, "clusterBootstrap");
        final List<Secret> secrets = new ArrayList<>();
        secrets.add(checkedCredentials.password());
        checkedClusterBootstrap.password().ifPresent(secrets::add);

        return List.copyOf(secrets);
    }

    private static final class BridgeCloseAction implements Runnable {

        private final TailingPostgresLogBridge bridge;

        private BridgeCloseAction(final TailingPostgresLogBridge bridge) {
            this.bridge = Objects.requireNonNull(bridge, "bridge");
        }

        @Override
        public void run() {
            bridge.close();
        }
    }
}
