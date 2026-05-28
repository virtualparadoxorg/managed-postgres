package eu.virtualparadox.managedpostgres.lifecycle.handle;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RestoreOptions;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

/**
 * Decorates a running PostgreSQL handle with a closeable side effect such as log forwarding.
 */
public final class LogBridgedRunningPostgres implements RunningPostgres {

    private final RunningPostgres delegate;
    private final Runnable closeAction;

    /**
     * Creates a decorated running PostgreSQL handle.
     *
     * @param delegate underlying running PostgreSQL handle
     * @param closeAction close action for side effects
     */
    public LogBridgedRunningPostgres(final RunningPostgres delegate, final Runnable closeAction) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PostgresConnectionInfo connectionInfo() {
        return delegate.connectionInfo();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PostgresStatus status() {
        return delegate.status();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void backupTo(final Path target) {
        delegate.backupTo(target);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restoreFrom(final Path backup, final RestoreOptions options) {
        delegate.restoreFrom(backup, options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        try {
            delegate.stop();
        } finally {
            closeQuietly();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        try {
            delegate.close();
        } finally {
            closeQuietly();
        }
    }

    /**
     * Returns optional startup telemetry from the wrapped running handle.
     *
     * @return immutable startup telemetry when the wrapped handle exposes it, otherwise zero telemetry
     */
    public StartupTelemetry startupTelemetry() {
        final Optional<Method> method = startupTelemetryMethod();
        final StartupTelemetry telemetry;
        if (method.isEmpty()) {
            telemetry = new StartupTelemetry(java.time.Duration.ZERO, 0);
        } else {
            telemetry = invokeStartupTelemetry(method.orElseThrow());
        }
        return telemetry;
    }

    private Optional<Method> startupTelemetryMethod() {
        Optional<Method> method = Optional.empty();
        boolean methodPresent = true;
        try {
            method = Optional.of(delegate.getClass().getDeclaredMethod("startupTelemetry"));
        } catch (NoSuchMethodException exception) {
            methodPresent = false;
        }
        final Optional<Method> resolvedMethod = methodPresent ? method : Optional.empty();
        return Objects.requireNonNull(resolvedMethod, "resolvedMethod");
    }

    private StartupTelemetry invokeStartupTelemetry(final Method method) {
        try {
            return (StartupTelemetry) method.invoke(delegate);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Unable to read startup telemetry from running PostgreSQL handle", exception);
        }
    }

    private void closeQuietly() {
        closeAction.run();
    }
}
