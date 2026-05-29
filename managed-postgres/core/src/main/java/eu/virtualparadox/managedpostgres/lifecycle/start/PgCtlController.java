package eu.virtualparadox.managedpostgres.lifecycle.start;

import eu.virtualparadox.managedpostgres.runtime.RuntimeBinaryLocator;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRequest;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;

/**
 * Builds and runs {@code pg_ctl} lifecycle commands.
 */
public final class PgCtlController {

    private final CommandRunner commandRunner;
    private final Path runtimeDirectory;

    /**
     * Creates a {@code pg_ctl} controller.
     *
     * @param commandRunner command runner
     * @param runtimeDirectory PostgreSQL runtime directory containing {@code bin/pg_ctl}
     */
    public PgCtlController(final CommandRunner commandRunner, final Path runtimeDirectory) {
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
        this.runtimeDirectory = Objects.requireNonNull(runtimeDirectory, "runtimeDirectory");
    }

    /**
     * Starts PostgreSQL with {@code pg_ctl start}.
     *
     * @param dataDirectory PostgreSQL data directory
     * @param logFile PostgreSQL log file
     * @param timeout command timeout
     * @return command result
     */
    public CommandResult start(final Path dataDirectory, final Path logFile, final Duration timeout) {
        final Path checkedDataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        final Path checkedLogFile = Objects.requireNonNull(logFile, "logFile");
        final List<String> command = List.of(
                pgCtlBinary().toString(),
                "-D",
                checkedDataDirectory.toString(),
                "-l",
                checkedLogFile.toString(),
                "-w",
                "start");

        return commandRunner.run(CommandRequest.of(command, timeout));
    }

    /**
     * Stops PostgreSQL with {@code pg_ctl stop}.
     *
     * @param dataDirectory PostgreSQL data directory
     * @param timeout command timeout
     * @return command result
     */
    public CommandResult stop(final Path dataDirectory, final Duration timeout) {
        final Path checkedDataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        final List<String> command = List.of(
                pgCtlBinary().toString(),
                "-D",
                checkedDataDirectory.toString(),
                "-m",
                "fast",
                "-w",
                "stop");

        return commandRunner.run(CommandRequest.of(command, timeout));
    }

    private Path pgCtlBinary() {
        return RuntimeBinaryLocator.resolveBinary(runtimeDirectory, "pg_ctl");
    }
}
