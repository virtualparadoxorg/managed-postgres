package eu.virtualparadox.managedpostgres.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.virtualparadox.managedpostgres.observe.ManagedPostgresProgressListener;
import eu.virtualparadox.managedpostgres.observe.PostgresLogLine;
import eu.virtualparadox.managedpostgres.observe.PostgresLogListener;
import eu.virtualparadox.managedpostgres.observe.StartupProgress;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

final class ManagedPostgresObserversTest {

    ManagedPostgresObserversTest() {}

    @Test
    void defaultsUseSlf4jProgressAndNoOpLog() {
        final ManagedPostgresObservers observers = ManagedPostgresObservers.defaults();

        assertThat(observers.progress()).isSameAs(ManagedPostgresProgressListener.slf4j());
        assertThat(observers.log()).isSameAs(PostgresLogListener.none());
    }

    @Test
    void withProgressReplacesProgressKeepingLog() {
        final ManagedPostgresObservers defaults = ManagedPostgresObservers.defaults();
        final ManagedPostgresProgressListener progress = new RecordingProgressListener();

        final ManagedPostgresObservers updated = defaults.withProgress(progress);

        assertThat(updated.progress()).isSameAs(progress);
        assertThat(updated.log()).isSameAs(defaults.log());
    }

    @Test
    void withLogReplacesLogKeepingProgress() {
        final ManagedPostgresObservers defaults = ManagedPostgresObservers.defaults();
        final PostgresLogListener log = new RecordingLogListener();

        final ManagedPostgresObservers updated = defaults.withLog(log);

        assertThat(updated.log()).isSameAs(log);
        assertThat(updated.progress()).isSameAs(defaults.progress());
    }

    @Test
    void constructorRejectsNullArguments() throws NoSuchMethodException {
        final Constructor<ManagedPostgresObservers> constructor = ManagedPostgresObservers.class.getConstructor(
                ManagedPostgresProgressListener.class, PostgresLogListener.class);

        assertThatExceptionOfType(InvocationTargetException.class)
                .isThrownBy(() -> constructor.newInstance(null, PostgresLogListener.none()))
                .satisfies(exception -> assertThat(exception.getCause())
                        .isInstanceOf(NullPointerException.class)
                        .hasMessage("progress"));
        assertThatExceptionOfType(InvocationTargetException.class)
                .isThrownBy(() -> constructor.newInstance(ManagedPostgresProgressListener.none(), null))
                .satisfies(exception -> assertThat(exception.getCause())
                        .isInstanceOf(NullPointerException.class)
                        .hasMessage("log"));
    }

    private static final class RecordingProgressListener implements ManagedPostgresProgressListener {
        @Override
        public void onProgress(final StartupProgress progress) {
            // no-op recorder placeholder
        }
    }

    private static final class RecordingLogListener implements PostgresLogListener {
        @Override
        public void onLogLine(final PostgresLogLine line) {
            // no-op recorder placeholder
        }
    }
}
