package eu.virtualparadox.managedpostgres.lifecycle.start;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class PgCtlControllerTest {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    private Path temporaryDirectory;

    PgCtlControllerTest() {}

    @Test
    void pgCtlStartCommandUsesArgumentListNotShellString() throws IOException {
        final Path runtimeDirectory = temporaryDirectory.resolve("runtime");
        final Path binDirectory = runtimeDirectory.resolve("bin");
        final Path capturedArguments = temporaryDirectory.resolve("arguments");
        final Path injectedFile = temporaryDirectory.resolve("injected");
        final Path pgCtl = binDirectory.resolve("pg_ctl");
        Files.createDirectories(binDirectory);
        Files.writeString(
                pgCtl,
                String.join(
                        System.lineSeparator(),
                        "#!/bin/sh",
                        "while [ \"$#\" -gt 0 ]; do",
                        "  printf '%s\\n' \"$1\"",
                        "  shift",
                        "done > \"" + capturedArguments + "\"",
                        ""),
                StandardCharsets.UTF_8);
        assertThat(pgCtl.toFile().setExecutable(true)).isTrue();
        final Path dataDirectory = temporaryDirectory.resolve("data dir; touch " + injectedFile.getFileName());
        final Path logFile = temporaryDirectory.resolve("postgres.log");
        final PgCtlController controller = new PgCtlController(new CommandRunner(), runtimeDirectory);

        final CommandResult result = controller.start(dataDirectory, logFile, COMMAND_TIMEOUT);

        assertThat(result.exitCode()).isZero();
        assertThat(Files.readAllLines(capturedArguments, StandardCharsets.UTF_8))
                .containsExactly("-D", dataDirectory.toString(), "-l", logFile.toString(), "-w", "start");
        assertThat(injectedFile).doesNotExist();
    }

    @Test
    void pgCtlUsesWindowsExecutableWhenPlainBinaryNameIsAbsent() throws IOException {
        final Path runtimeDirectory = temporaryDirectory.resolve("runtime");
        final Path binDirectory = runtimeDirectory.resolve("bin");
        final Path dataDirectory = temporaryDirectory.resolve("data");
        final Path logFile = temporaryDirectory.resolve("postgres.log");
        final Path capturedCommand = temporaryDirectory.resolve("windows-command");
        final Path pgCtl = binDirectory.resolve("pg_ctl.exe");
        Files.createDirectories(binDirectory);
        Files.writeString(
                pgCtl,
                String.join(
                        System.lineSeparator(), "#!/bin/sh", "printf '%s' \"$0\" > \"" + capturedCommand + "\"", ""),
                StandardCharsets.UTF_8);
        assertThat(pgCtl.toFile().setExecutable(true)).isTrue();
        final PgCtlController controller = new PgCtlController(new CommandRunner(), runtimeDirectory);

        controller.start(dataDirectory, logFile, COMMAND_TIMEOUT);

        assertThat(capturedCommand).hasContent(pgCtl.toString());
    }
}
