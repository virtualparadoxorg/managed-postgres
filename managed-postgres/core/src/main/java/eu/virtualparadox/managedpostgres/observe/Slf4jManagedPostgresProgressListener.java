package eu.virtualparadox.managedpostgres.observe;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Progress listener that logs every event via SLF4J at INFO level.
 */
final class Slf4jManagedPostgresProgressListener implements ManagedPostgresProgressListener {

    /** Shared singleton instance. */
    static final Slf4jManagedPostgresProgressListener INSTANCE = new Slf4jManagedPostgresProgressListener();

    private static final Logger LOGGER = LoggerFactory.getLogger("eu.virtualparadox.managedpostgres");

    private Slf4jManagedPostgresProgressListener() {}

    @Override
    public void onProgress(final StartupProgress progress) {
        final StartupProgress checkedProgress = Objects.requireNonNull(progress, "progress");
        final int percent = checkedProgress.percent();
        final String message = sanitize(checkedProgress.message());
        if (checkedProgress.phase() == StartupPhase.DOWNLOADING && percent >= 0) {
            LOGGER.info("{} {}% — {}", checkedProgress.phase(), percent, message);
        } else {
            LOGGER.info("{} — {}", checkedProgress.phase(), message);
        }
    }

    private static String sanitize(final String message) {
        return message.replace('\n', ' ').replace('\r', ' ');
    }
}
