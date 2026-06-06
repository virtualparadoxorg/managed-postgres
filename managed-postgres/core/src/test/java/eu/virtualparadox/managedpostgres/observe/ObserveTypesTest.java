package eu.virtualparadox.managedpostgres.observe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

final class ObserveTypesTest {

    ObserveTypesTest() {}

    @Test
    void percentReturnsMinusOneWhenTotalIsZero() {
        assertThat(new StartupProgress(StartupPhase.READY, 0, 0, "ready").percent())
                .isEqualTo(-1);
    }

    @Test
    void percentReturnsMinusOneWhenTotalIsUnknownButBytesCompleted() {
        assertThat(new StartupProgress(StartupPhase.DOWNLOADING, 50, 0, "downloading").percent())
                .isEqualTo(-1);
    }

    @Test
    void percentComputesRatioWhenTotalKnown() {
        assertThat(new StartupProgress(StartupPhase.DOWNLOADING, 50, 200, "downloading").percent())
                .isEqualTo(25);
    }

    @Test
    void phaseFactoryUsesZeroBytes() {
        final StartupProgress progress = StartupProgress.phase(StartupPhase.STARTING, "starting");

        assertThat(progress.completedBytes()).isZero();
        assertThat(progress.totalBytes()).isZero();
        assertThat(progress.percent()).isEqualTo(-1);
    }

    @Test
    void startupProgressRejectsNullPhase() throws NoSuchMethodException {
        final Constructor<StartupProgress> constructor =
                StartupProgress.class.getConstructor(StartupPhase.class, long.class, long.class, String.class);

        assertThatExceptionOfType(InvocationTargetException.class)
                .isThrownBy(() -> constructor.newInstance(null, 0L, 0L, "message"))
                .satisfies(exception -> assertThat(exception.getCause())
                        .isInstanceOf(NullPointerException.class)
                        .hasMessage("phase"));
    }

    @Test
    void startupProgressRejectsNullMessage() throws NoSuchMethodException {
        final Constructor<StartupProgress> constructor =
                StartupProgress.class.getConstructor(StartupPhase.class, long.class, long.class, String.class);

        assertThatExceptionOfType(InvocationTargetException.class)
                .isThrownBy(() -> constructor.newInstance(StartupPhase.READY, 0L, 0L, null))
                .satisfies(exception -> assertThat(exception.getCause())
                        .isInstanceOf(NullPointerException.class)
                        .hasMessage("message"));
    }

    @Test
    void postgresLogLineRejectsNullFields() throws NoSuchMethodException {
        final Constructor<PostgresLogLine> constructor =
                PostgresLogLine.class.getConstructor(PostgresLogLevel.class, PostgresLogSource.class, String.class);

        assertThatExceptionOfType(InvocationTargetException.class)
                .isThrownBy(() -> constructor.newInstance(null, PostgresLogSource.SERVER, "m"))
                .satisfies(exception -> assertThat(exception.getCause())
                        .isInstanceOf(NullPointerException.class)
                        .hasMessage("level"));
        assertThatExceptionOfType(InvocationTargetException.class)
                .isThrownBy(() -> constructor.newInstance(PostgresLogLevel.LOG, null, "m"))
                .satisfies(exception -> assertThat(exception.getCause())
                        .isInstanceOf(NullPointerException.class)
                        .hasMessage("source"));
        assertThatExceptionOfType(InvocationTargetException.class)
                .isThrownBy(() -> constructor.newInstance(PostgresLogLevel.LOG, PostgresLogSource.SERVER, null))
                .satisfies(exception -> assertThat(exception.getCause())
                        .isInstanceOf(NullPointerException.class)
                        .hasMessage("message"));
    }

    @Test
    void noneProgressListenerIsSingletonAndIgnoresEvents() {
        final ManagedPostgresProgressListener none = ManagedPostgresProgressListener.none();

        assertThat(none).isSameAs(ManagedPostgresProgressListener.none());
        none.onProgress(new StartupProgress(StartupPhase.READY, 0, 0, "ready"));
    }

    @Test
    void noneLogListenerIsSingletonAndIgnoresLines() {
        final PostgresLogListener none = PostgresLogListener.none();

        assertThat(none).isSameAs(PostgresLogListener.none());
        assertThat(none.isActive()).isFalse();
        none.onLogLine(new PostgresLogLine(PostgresLogLevel.LOG, PostgresLogSource.SERVER, "line"));
    }

    @Test
    void customLogListenerIsActiveByDefault() {
        final PostgresLogListener listener = line -> {
            // Intentionally ignores the line; only the default activity flag matters here.
        };

        assertThat(listener.isActive()).isTrue();
    }

    @Test
    void slf4jProgressListenerLogsDownloadingWithPercentAndOtherPhases() {
        final ManagedPostgresProgressListener slf4j = ManagedPostgresProgressListener.slf4j();

        assertThat(slf4j).isSameAs(ManagedPostgresProgressListener.slf4j());
        slf4j.onProgress(new StartupProgress(StartupPhase.DOWNLOADING, 50, 200, "downloading"));
        slf4j.onProgress(new StartupProgress(StartupPhase.DOWNLOADING, 0, 0, "downloading"));
        slf4j.onProgress(new StartupProgress(StartupPhase.READY, 0, 0, "ready"));
    }
}
