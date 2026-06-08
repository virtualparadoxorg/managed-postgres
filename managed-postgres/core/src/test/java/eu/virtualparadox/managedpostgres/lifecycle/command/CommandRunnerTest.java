package eu.virtualparadox.managedpostgres.lifecycle.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class CommandRunnerTest {

    private static final Duration COMMAND_COMPLETION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration COMMAND_TIMEOUT = Duration.ofMillis(100);
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    private Path temporaryDirectory;

    CommandRunnerTest() {}

    @Test
    void commandRunnerCapturesStdout() throws IOException {
        final Path script = createScript("stdout.sh", "printf 'ready\\n'");
        final CommandRequest request = CommandRequest.of(List.of(script.toString()), COMMAND_COMPLETION_TIMEOUT);

        final CommandResult result = new CommandRunner().run(request);

        assertThat(result.stdout()).isEqualTo("ready\n");
    }

    @Test
    void commandRunnerCapturesStderr() throws IOException {
        final Path script = createScript("stderr.sh", "printf 'warning\\n' >&2");
        final CommandRequest request = CommandRequest.of(List.of(script.toString()), COMMAND_COMPLETION_TIMEOUT);

        final CommandResult result = new CommandRunner().run(request);

        assertThat(result.stderr()).isEqualTo("warning\n");
    }

    @Test
    void commandRunnerReturnsExitCode() throws IOException {
        final Path script = createScript("exit-code.sh", "exit 7");
        final CommandRequest request = CommandRequest.of(List.of(script.toString()), COMMAND_COMPLETION_TIMEOUT);

        final CommandResult result = new CommandRunner().run(request);

        assertThat(result.exitCode()).isEqualTo(7);
    }

    @Test
    void commandTimeoutKillsProcess() throws IOException, InterruptedException {
        final Path finished = temporaryDirectory.resolve("finished");
        // The script sleeps far longer than the 100ms command timeout, then would create the marker
        // file. A long sleep keeps a wide margin so a loaded CI runner cannot let the process reach
        // the marker write before the timeout kills it (this test was flaky with a 5s sleep).
        final Path script = createScript("timeout.sh", "sleep 30\nprintf 'done' > \"" + finished + "\"");
        final CommandRequest request = CommandRequest.of(List.of(script.toString()), COMMAND_TIMEOUT);

        assertThatThrownBy(() -> new CommandRunner().run(request))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("timed out");

        park(Duration.ofMillis(300));
        assertThat(finished).doesNotExist();
    }

    @Test
    void interruptedWaitResetsInterruptFlagAndThrowsDomainException() throws IOException, InterruptedException {
        final Path started = temporaryDirectory.resolve("started");
        final Path script = createScript("interrupt.sh", "printf 'started' > \"" + started + "\"\nsleep 5");
        final CommandRequest request = CommandRequest.of(List.of(script.toString()), Duration.ofSeconds(30));
        final AtomicBoolean interruptedFlag = new AtomicBoolean();
        final AtomicReference<ManagedPostgresException> thrown = new AtomicReference<>();
        final Thread worker = new Thread(() -> {
            try {
                new CommandRunner().run(request);
            } catch (final ManagedPostgresException failure) {
                interruptedFlag.set(Thread.currentThread().isInterrupted());
                thrown.set(failure);
            }
        });

        worker.start();
        waitFor(started);
        worker.interrupt();
        worker.join(WAIT_TIMEOUT.toMillis());

        assertThat(worker.isAlive()).isFalse();
        assertThat(thrown.get()).isInstanceOf(ManagedPostgresException.class);
        assertThat(interruptedFlag).isTrue();
    }

    @Test
    void commandRenderingRedactsSecrets() {
        final CommandRequest request =
                CommandRequest.of(List.of("pg_ctl", "start", "password=actual-secret"), COMMAND_COMPLETION_TIMEOUT);

        assertThat(request.renderedCommand()).contains("password=<redacted>").doesNotContain("actual-secret");
    }

    @Test
    void commandRequestRendersQuotedArgumentsAndCopiesEnvironment() {
        final CommandRequest request = CommandRequest.of(
                        List.of("pg_ctl", "start postgres", "tab\tvalue", "quote\"value"), COMMAND_COMPLETION_TIMEOUT)
                .withEnvironmentVariable("PGDATA", "pg data")
                .withWorkingDirectory(temporaryDirectory);

        assertThat(request.renderedCommand()).isEqualTo("pg_ctl \"start postgres\" \"tab\tvalue\" quote\"value");
        assertThat(request.environment()).containsEntry("PGDATA", "pg data");
        assertThat(request.workingDirectory()).hasValue(temporaryDirectory);
    }

    @Test
    void commandRequestRejectsInvalidValues() {
        assertThatThrownBy(() -> CommandRequest.of(List.of(), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommandRequest.of(List.of("pg_ctl", " "), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommandRequest.of(List.of("pg_ctl"), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommandRequest.of(List.of("pg_ctl"), Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> {
                    final CommandRequest request = CommandRequest.of(List.of("pg_ctl"), Duration.ofSeconds(1))
                            .withEnvironmentVariable(" ", "value");
                    assertThat(request).isNotNull();
                })
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void commandRunnerWrapsStartFailureWithRedactedCommand() {
        final CommandRequest request = CommandRequest.of(
                List.of(temporaryDirectory.resolve("missing command").toString(), "password=actual-secret"),
                COMMAND_COMPLETION_TIMEOUT);

        assertThatThrownBy(() -> new CommandRunner().run(request))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("failed to start")
                .satisfies(throwable -> assertThat(((ManagedPostgresException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("password=<redacted>")
                        .doesNotContain("actual-secret"));
    }

    private Path createScript(final String fileName, final String body) throws IOException {
        final Path script = temporaryDirectory.resolve(fileName);
        Files.writeString(script, "#!/bin/sh\n" + body + "\n", StandardCharsets.UTF_8);
        assertThat(script.toFile().setExecutable(true)).isTrue();

        return script;
    }

    private static void waitFor(final Path path) throws InterruptedException {
        final long deadline = System.nanoTime() + WAIT_TIMEOUT.toNanos();
        while (!Files.exists(path) && System.nanoTime() < deadline) {
            park(Duration.ofMillis(25));
        }
        assertThat(path).exists();
    }

    private static void park(final Duration duration) {
        LockSupport.parkNanos(duration.toNanos());
    }
}
