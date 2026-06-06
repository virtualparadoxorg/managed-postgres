package eu.virtualparadox.managedpostgres.runtime.download.progress;

import eu.virtualparadox.managedpostgres.observe.ManagedPostgresProgressListener;
import eu.virtualparadox.managedpostgres.observe.StartupPhase;
import eu.virtualparadox.managedpostgres.observe.StartupProgress;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Adapts raw {@code (done, total)} byte-transfer callbacks into throttled {@code DOWNLOADING}
 * progress events delivered to a {@link ManagedPostgresProgressListener}.
 *
 * <p>To avoid event spam on chunked reads, an intermediate event is forwarded only when the integer
 * {@link StartupProgress#percent()} has increased by at least one OR at least {@code 250 ms} have
 * elapsed since the previous forwarded event. A final {@code 100 %} event (when
 * {@code done == total}) is always delivered.
 */
public final class DownloadProgressReporter implements BytesTransferredListener {

    private static final long MIN_INTERVAL_NANOS = 250L * 1_000_000L;
    private static final String MESSAGE = "Downloading PostgreSQL runtime";

    private final ManagedPostgresProgressListener listener;
    private final LongSupplier nanoClock;

    private boolean reported;
    private int lastPercent = -2;
    private long lastEmittedNanos;

    /**
     * Creates a download progress reporter backed by the wall clock.
     *
     * @param listener progress listener that receives throttled download events
     */
    public DownloadProgressReporter(final ManagedPostgresProgressListener listener) {
        this(listener, System::nanoTime);
    }

    /**
     * Creates a download progress reporter with an injectable nanosecond clock.
     *
     * @param listener progress listener that receives throttled download events
     * @param nanoClock monotonic nanosecond clock supplier
     */
    public DownloadProgressReporter(final ManagedPostgresProgressListener listener, final LongSupplier nanoClock) {
        this.listener = Objects.requireNonNull(listener, "listener");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    @Override
    public void onBytesTransferred(final long done, final long total) {
        final StartupProgress progress = new StartupProgress(StartupPhase.DOWNLOADING, done, total, MESSAGE);
        if (shouldEmit(progress, done, total)) {
            emit(progress);
        }
    }

    private boolean shouldEmit(final StartupProgress progress, final long done, final long total) {
        final boolean emit;
        if (isFinal(done, total)) {
            emit = true;
        } else if (!reported) {
            emit = true;
        } else if (progress.percent() > lastPercent) {
            emit = true;
        } else {
            emit = nanoClock.getAsLong() - lastEmittedNanos >= MIN_INTERVAL_NANOS;
        }

        return emit;
    }

    private static boolean isFinal(final long done, final long total) {
        return total > 0 && done >= total;
    }

    private void emit(final StartupProgress progress) {
        listener.onProgress(progress);
        reported = true;
        lastPercent = progress.percent();
        lastEmittedNanos = nanoClock.getAsLong();
    }
}
