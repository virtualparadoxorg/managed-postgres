package eu.virtualparadox.managedpostgres.runtime.download.progress;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.observe.ManagedPostgresProgressListener;
import eu.virtualparadox.managedpostgres.observe.StartupPhase;
import eu.virtualparadox.managedpostgres.observe.StartupProgress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

final class DownloadProgressReporterTest {

    DownloadProgressReporterTest() {}

    @Test
    void emitsDownloadingPhaseWithMonotonicNonDecreasingPercentages() {
        final RecordingListener listener = new RecordingListener();
        final FixedClock clock = new FixedClock();
        final DownloadProgressReporter reporter = new DownloadProgressReporter(listener, clock);

        for (long done = 0; done <= 100; done += 10) {
            reporter.onBytesTransferred(done, 100);
        }

        assertThat(listener.events()).extracting(StartupProgress::phase).containsOnly(StartupPhase.DOWNLOADING);
        assertThat(listener.events()).extracting(StartupProgress::percent).isSorted();
        assertThat(listener.events())
                .extracting(StartupProgress::percent)
                .startsWith(0)
                .endsWith(100);
    }

    @Test
    void throttlesIntermediateEventsToPercentIncreaseWithinTheSameTimeWindow() {
        final RecordingListener listener = new RecordingListener();
        final FixedClock clock = new FixedClock();
        final DownloadProgressReporter reporter = new DownloadProgressReporter(listener, clock);

        // Many byte updates that map to the same integer percent should collapse to one event.
        reporter.onBytesTransferred(0, 1000);
        reporter.onBytesTransferred(1, 1000);
        reporter.onBytesTransferred(2, 1000);
        reporter.onBytesTransferred(3, 1000);

        assertThat(listener.events()).hasSize(1);
        assertThat(listener.events().get(0).percent()).isZero();
    }

    @Test
    void emitsWhenTwoHundredFiftyMillisecondsElapseEvenWithoutPercentChange() {
        final RecordingListener listener = new RecordingListener();
        final FixedClock clock = new FixedClock();
        final DownloadProgressReporter reporter = new DownloadProgressReporter(listener, clock);

        reporter.onBytesTransferred(0, 1000);
        clock.advanceMillis(300);
        reporter.onBytesTransferred(1, 1000);

        assertThat(listener.events()).hasSize(2);
    }

    @Test
    void alwaysDeliversFinalHundredPercentEvent() {
        final RecordingListener listener = new RecordingListener();
        final FixedClock clock = new FixedClock();
        final DownloadProgressReporter reporter = new DownloadProgressReporter(listener, clock);

        reporter.onBytesTransferred(0, 100);
        reporter.onBytesTransferred(50, 100);
        reporter.onBytesTransferred(100, 100);

        final StartupProgress last = listener.events().get(listener.events().size() - 1);
        assertThat(last.completedBytes()).isEqualTo(last.totalBytes());
        assertThat(last.percent()).isEqualTo(100);
    }

    @Test
    void unknownTotalYieldsPercentMinusOneThroughout() {
        final RecordingListener listener = new RecordingListener();
        final FixedClock clock = new FixedClock();
        final DownloadProgressReporter reporter = new DownloadProgressReporter(listener, clock);

        reporter.onBytesTransferred(0, 0);
        clock.advanceMillis(300);
        reporter.onBytesTransferred(4096, 0);
        clock.advanceMillis(300);
        reporter.onBytesTransferred(8192, 0);

        assertThat(listener.events()).isNotEmpty();
        assertThat(listener.events()).extracting(StartupProgress::percent).containsOnly(-1);
    }

    private static final class RecordingListener implements ManagedPostgresProgressListener {

        private final List<StartupProgress> events = new ArrayList<>();

        @Override
        public void onProgress(final StartupProgress progress) {
            events.add(progress);
        }

        private List<StartupProgress> events() {
            return List.copyOf(events);
        }
    }

    private static final class FixedClock implements LongSupplier {

        private long nanos;

        private void advanceMillis(final long millis) {
            nanos += millis * 1_000_000L;
        }

        @Override
        public long getAsLong() {
            return nanos;
        }
    }
}
