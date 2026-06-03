package eu.virtualparadox.managedpostgres.lifecycle.log;

import eu.virtualparadox.managedpostgres.diagnostics.CommandRedactor;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.apache.commons.lang3.StringUtils;

/**
 * Tails the managed PostgreSQL log file and forwards new lines to a sink.
 */
public final class TailingPostgresLogBridge implements AutoCloseable {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);
    private static final String REDACTED = "<redacted>";
    private static final int COPY_BUFFER_BYTES = 4_096;

    private final Path logFile;
    private final String loggerName;
    private final List<String> secrets;
    private final PostgresLogSink sink;
    private final AtomicBoolean running;
    private final Thread thread;

    private long position;
    private String partialLine;

    /**
     * Creates and starts a log bridge.
     *
     * @param logFile PostgreSQL log file
     * @param loggerName SLF4J logger name
     * @param secrets secret values to redact from forwarded log lines
     * @param sink forwarding sink
     */
    public TailingPostgresLogBridge(
            final Path logFile, final String loggerName, final List<Secret> secrets, final PostgresLogSink sink) {
        this.logFile = Objects.requireNonNull(logFile, "logFile");
        if (StringUtils.isBlank(loggerName)) {
            throw new IllegalArgumentException("loggerName must not be blank");
        }
        this.loggerName = loggerName;
        this.secrets = Objects.requireNonNull(secrets, "secrets").stream()
                .map(secret -> Objects.requireNonNull(secret, "secret").reveal())
                .filter(StringUtils::isNotEmpty)
                .toList();
        this.sink = Objects.requireNonNull(sink, "sink");
        running = new AtomicBoolean(true);
        position = initialPosition(logFile);
        partialLine = "";
        thread = new Thread(this::runLoop, "managed-postgres-log-bridge");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        if (running.compareAndSet(true, false)) {
            thread.interrupt();
            joinThread();
        }
    }

    private void runLoop() {
        while (running.get()) {
            try {
                forwardNewLines();
                sleep();
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                running.set(false);
            } catch (final IOException ignored) {
                sleepQuietly();
            }
        }
    }

    private void forwardNewLines() throws IOException {
        final boolean logFileExists = Files.exists(logFile);
        if (logFileExists) {
            final long fileSize = Files.size(logFile);
            if (fileSize < position) {
                position = 0L;
                partialLine = "";
            }
            if (fileSize > position) {
                final String appendedText = readAppendedText(fileSize);
                forwardCompleteLines(partialLine + appendedText);
                position = fileSize;
            }
        }
    }

    private String readAppendedText(final long fileSize) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r")) {
            file.seek(position);
            final byte[] chunk = new byte[COPY_BUFFER_BYTES];
            final StringBuilder appendedText = new StringBuilder();
            long remaining = fileSize - position;
            while (remaining > 0L) {
                final int bytesToRead = (int) Math.min(chunk.length, remaining);
                final int read = file.read(chunk, 0, bytesToRead);
                if (read < 0) {
                    remaining = 0L;
                } else {
                    appendedText.append(new String(chunk, 0, read, StandardCharsets.UTF_8));
                    remaining -= read;
                }
            }

            return appendedText.toString();
        }
    }

    private void forwardCompleteLines(final String text) {
        final String normalizedText = text.replace("\r\n", "\n").replace('\r', '\n');
        final String[] lines = normalizedText.split("\n", -1);
        final int lastIndex = lines.length - 1;
        for (int index = 0; index < lastIndex; index++) {
            sink.log(loggerName, redact(lines[index]));
        }
        partialLine = lines[lastIndex];
    }

    private String redact(final String line) {
        String redactedLine = CommandRedactor.redact(Objects.requireNonNull(line, "line"));
        for (final String secret : secrets) {
            redactedLine = redactedLine.replace(secret, REDACTED);
        }

        return redactedLine;
    }

    private void sleep() throws InterruptedException {
        LockSupport.parkNanos(POLL_INTERVAL.toNanos());
        if (Thread.interrupted()) {
            throw new InterruptedException("managed postgres log bridge interrupted");
        }
    }

    private void sleepQuietly() {
        try {
            sleep();
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }

    private void joinThread() {
        boolean joined = false;
        while (!joined) {
            try {
                thread.join();
                joined = true;
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                joined = true;
            }
        }
    }

    private static long initialPosition(final Path logFile) {
        long initialPosition = 0L;
        try {
            if (Files.exists(logFile)) {
                initialPosition = Files.size(logFile);
            }
        } catch (final IOException ignored) {
            initialPosition = 0L;
        }

        return initialPosition;
    }
}
