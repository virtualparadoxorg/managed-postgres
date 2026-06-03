package eu.virtualparadox.managedpostgres.lifecycle.log;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class TailingPostgresLogBridgeTest {

    @TempDir
    private Path temporaryDirectory;

    TailingPostgresLogBridgeTest() {}

    @Test
    void bridgeStreamsOnlyNewLogLinesAndRedactsSecrets() throws IOException {
        final Path logFile = temporaryDirectory.resolve("postgres.log");
        Files.writeString(logFile, "existing line\n", StandardCharsets.UTF_8);
        final List<String> recordedLines = new CopyOnWriteArrayList<>();

        try (TailingPostgresLogBridge bridge = new TailingPostgresLogBridge(
                logFile,
                "managed.postgres.test",
                List.of(Secret.of("runtime-secret")),
                (loggerName, line) -> recordedLines.add(loggerName + ":" + line))) {
            assertThat(bridge).isNotNull();
            Files.writeString(
                    logFile,
                    "new runtime-secret line\n",
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);

            awaitLines(recordedLines, List.of("managed.postgres.test:new <redacted> line"));
        }
    }

    @Test
    void closingBridgeStopsFurtherForwarding() throws IOException {
        final Path logFile = emptyLogFile("postgres.log");
        final List<String> recordedLines = new CopyOnWriteArrayList<>();
        try (TailingPostgresLogBridge bridge = createBridge(logFile, recordedLines)) {
            assertThat(bridge).isNotNull();
            appendLine(logFile, "first line\n");
            awaitLines(recordedLines, List.of("managed.postgres.test:first line"));
        }

        appendLine(logFile, "second line\n");
        ThreadSupport.sleep(Duration.ofMillis(300));
        assertThat(recordedLines).containsExactly("managed.postgres.test:first line");
    }

    @Test
    void bridgeWaitsForMissingFileAndForwardsOnceFileAppears() throws IOException {
        final Path logFile = temporaryDirectory.resolve("postgres.log");
        final List<String> recordedLines = new CopyOnWriteArrayList<>();

        try (TailingPostgresLogBridge bridge = new TailingPostgresLogBridge(
                logFile,
                "managed.postgres.test",
                List.of(),
                (loggerName, line) -> recordedLines.add(loggerName + ":" + line))) {
            assertThat(bridge).isNotNull();
            Files.writeString(logFile, "appeared line\n", StandardCharsets.UTF_8);

            awaitLines(recordedLines, List.of("managed.postgres.test:appeared line"));
        }
    }

    @Test
    void bridgeResetsReadPositionAfterLogTruncation() throws IOException {
        final Path logFile = emptyLogFile("postgres.log");
        final List<String> recordedLines = new CopyOnWriteArrayList<>();

        try (TailingPostgresLogBridge bridge = createBridge(logFile, recordedLines)) {
            assertThat(bridge).isNotNull();
            appendLine(logFile, "first line\n");
            awaitLines(recordedLines, List.of("managed.postgres.test:first line"));

            Files.writeString(logFile, "", StandardCharsets.UTF_8);
            appendLine(logFile, "new\n");

            awaitLines(recordedLines, List.of("managed.postgres.test:first line", "managed.postgres.test:new"));
        }
    }

    private Path emptyLogFile(final String fileName) throws IOException {
        final Path logFile = temporaryDirectory.resolve(fileName);
        Files.writeString(logFile, "", StandardCharsets.UTF_8);

        return logFile;
    }

    private static TailingPostgresLogBridge createBridge(final Path logFile, final List<String> recordedLines) {
        return new TailingPostgresLogBridge(
                logFile,
                "managed.postgres.test",
                List.of(),
                (loggerName, line) -> recordedLines.add(loggerName + ":" + line));
    }

    private static void appendLine(final Path logFile, final String line) throws IOException {
        Files.writeString(logFile, line, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
    }

    private static void awaitLines(final List<String> actualLines, final List<String> expectedLines) {
        final long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        boolean matched = false;
        while (!matched && System.nanoTime() < deadline) {
            matched = actualLines.equals(expectedLines);
            if (!matched) {
                ThreadSupport.sleep(Duration.ofMillis(25));
            }
        }
        assertThat(actualLines).containsExactlyElementsOf(expectedLines);
    }

    private static final class ThreadSupport {

        private ThreadSupport() {}

        private static void sleep(final Duration duration) {
            LockSupport.parkNanos(duration.toNanos());
            if (Thread.interrupted()) {
                throw new AssertionError("Test thread was interrupted");
            }
        }
    }
}
