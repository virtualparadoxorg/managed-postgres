package eu.virtualparadox.managedpostgres.lifecycle.probe;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class PostgresReadinessWaiterTest {

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(2);

    @TempDir
    private Path temporaryDirectory;

    PostgresReadinessWaiterTest() {
    }

    @Test
    void waiterCountsUnhealthyPollsBeforeSuccessfulReadiness() throws IOException {
        final Path runtimeDirectory = runtimeWithCountingPgIsReady(2);
        final PostgresReadinessWaiter waiter = new PostgresReadinessWaiter(new CommandRunner(), STARTUP_TIMEOUT);

        final PostgresReadinessWaiter.ReadinessOutcome outcome = waiter.await(
                runtimeDirectory,
                connectionInfo(),
                PostgresLayout.plan(
                        new Storage(temporaryDirectory.resolve("postgres"), false),
                        new FileSystemOperationJournal()));

        assertThat(outcome.finalResult().healthy()).isTrue();
        assertThat(outcome.failedHealthcheckCount()).isEqualTo(2);
    }

    private Path runtimeWithCountingPgIsReady(final int failingAttempts) throws IOException {
        final Path runtimeDirectory = temporaryDirectory.resolve("runtime");
        final Path binDirectory = runtimeDirectory.resolve("bin");
        final Path stateFile = temporaryDirectory.resolve("pg-isready-attempts.txt");
        Files.createDirectories(binDirectory);
        Files.writeString(
                binDirectory.resolve("pg_isready"),
                "#!/bin/sh\n"
                        + "STATE=" + shellQuote(stateFile) + "\n"
                        + "if [ -f \"$STATE\" ]; then ATTEMPTS=$(cat \"$STATE\"); else ATTEMPTS=0; fi\n"
                        + "ATTEMPTS=$((ATTEMPTS + 1))\n"
                        + "printf '%s' \"$ATTEMPTS\" > \"$STATE\"\n"
                        + "if [ \"$ATTEMPTS\" -le " + failingAttempts + " ]; then\n"
                        + "  printf 'rejecting\\n' >&2\n"
                        + "  exit 2\n"
                        + "fi\n"
                        + "printf 'accepting\\n'\n"
                        + "exit 0\n",
                StandardCharsets.UTF_8);
        assertThat(binDirectory.resolve("pg_isready").toFile().setExecutable(true)).isTrue();

        return runtimeDirectory;
    }

    private static PostgresConnectionInfo connectionInfo() {
        return new PostgresConnectionInfo("127.0.0.1", 15432, "postgres", "postgres", Secret.redacted());
    }

    private static String shellQuote(final Path path) {
        return "'" + path.toAbsolutePath().normalize().toString().replace("'", "'\"'\"'") + "'";
    }
}
