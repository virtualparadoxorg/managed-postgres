package eu.virtualparadox.managedpostgres.runtime.packaging.build.execution;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Executes pre-split native commands without involving a shell string.
 */
public final class ProcessCommandExecutor {

    /**
     * Creates a native process executor for pre-split command arguments.
     */
    public ProcessCommandExecutor() {}

    /**
     * Executes a native command in the supplied working directory.
     *
     * @param command pre-split command arguments
     * @param workingDirectory command working directory
     * @param environmentOverrides environment variables to merge into the process environment
     * @param failureMessage message to use when process launch fails
     */
    public void runCommand(
            final List<String> command,
            final Path workingDirectory,
            final Map<String, String> environmentOverrides,
            final String failureMessage) {
        final ProcessBuilder processBuilder = new ProcessBuilder(Objects.requireNonNull(command, "command"));
        processBuilder.directory(
                Objects.requireNonNull(workingDirectory, "workingDirectory").toFile());
        processBuilder.redirectErrorStream(true);
        processBuilder
                .environment()
                .putAll(Map.copyOf(Objects.requireNonNull(environmentOverrides, "environmentOverrides")));
        try {
            final Path outputFile = Files.createTempFile("managed-postgres-build-", ".log");
            try {
                processBuilder.redirectOutput(outputFile.toFile());
                final Process process = processBuilder.start();
                final int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new IllegalStateException("command failed: " + String.join(" ", command) + "\n"
                            + Files.readString(outputFile, StandardCharsets.UTF_8));
                }
            } finally {
                Files.deleteIfExists(outputFile);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(Objects.requireNonNull(failureMessage, "failureMessage"), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("native command was interrupted", exception);
        }
    }
}
