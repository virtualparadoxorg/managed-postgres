package eu.virtualparadox.managedpostgres.lifecycle.command;

import eu.virtualparadox.managedpostgres.diagnostics.CommandRedactor;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Immutable request for executing one external command.
 *
 * @param command command executable and arguments
 * @param environment additional environment variables
 * @param workingDirectory working directory, when the command requires one
 * @param timeout maximum time to wait for process completion
 */
public record CommandRequest(
        List<String> command,
        Map<String, String> environment,
        Optional<Path> workingDirectory,
        Duration timeout) {

    /**
     * Creates an immutable command request.
     *
     * @param command command executable and arguments
     * @param environment additional environment variables
     * @param workingDirectory working directory, when the command requires one
     * @param timeout maximum time to wait for process completion
     */
    public CommandRequest {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        timeout = requirePositiveTimeout(timeout);
        command = List.copyOf(command);
        environment = Map.copyOf(environment);
        requireCommand(command);
    }

    /**
     * Creates a command request with no additional environment or working directory.
     *
     * @param command command executable and arguments
     * @param timeout maximum time to wait for process completion
     * @return command request
     */
    public static CommandRequest of(final List<String> command, final Duration timeout) {
        return new CommandRequest(command, Map.of(), Optional.empty(), timeout);
    }

    /**
     * Returns a copy with one additional environment variable.
     *
     * @param name environment variable name
     * @param value environment variable value
     * @return command request with the environment variable
     */
    public CommandRequest withEnvironmentVariable(final String name, final String value) {
        final String checkedName = requireNotBlank(name, "name");
        final String checkedValue = Objects.requireNonNull(value, "value");
        final Map<String, String> updatedEnvironment = new LinkedHashMap<>(environment);
        updatedEnvironment.put(checkedName, checkedValue);

        return new CommandRequest(command, updatedEnvironment, workingDirectory, timeout);
    }

    /**
     * Returns a copy with the supplied working directory.
     *
     * @param directory command working directory
     * @return command request with a working directory
     */
    public CommandRequest withWorkingDirectory(final Path directory) {
        return new CommandRequest(
                command,
                environment,
                Optional.of(Objects.requireNonNull(directory, "directory")),
                timeout);
    }

    /**
     * Renders the command and redacts known secret values.
     *
     * @return redacted command rendering
     */
    public String renderedCommand() {
        return CommandRedactor.redact(command.stream()
                .map(CommandRequest::renderArgument)
                .collect(Collectors.joining(" ")));
    }

    private static void requireCommand(final List<String> command) {
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        command.forEach(argument -> requireNotBlank(argument, "command argument"));
    }

    private static Duration requirePositiveTimeout(final Duration timeout) {
        final Duration checkedTimeout = Objects.requireNonNull(timeout, "timeout");
        if (checkedTimeout.isZero() || checkedTimeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }

        return checkedTimeout;
    }

    private static String requireNotBlank(final String value, final String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value;
    }

    private static String renderArgument(final String argument) {
        final String rendered;
        if (argument.contains(" ") || argument.contains("\t")) {
            rendered = '"' + argument.replace("\"", "\\\"") + '"';
        } else {
            rendered = argument;
        }

        return rendered;
    }
}
