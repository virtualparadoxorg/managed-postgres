package eu.virtualparadox.managedpostgres.runtime.packaging.build;

import eu.virtualparadox.managedpostgres.runtime.packaging.build.execution.ProcessCommandExecutor;
import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Executes PostgreSQL source builds on runner-native Unix environments.
 */
public final class SourceBuildExecutor implements BuildExecutor {

    private final Map<String, String> environmentOverrides;
    private final int parallelJobs;
    private final List<String> makeCommand;
    private final ProcessCommandExecutor processCommandExecutor;

    /**
     * Creates a source build executor using the process environment and detected CPU parallelism.
     */
    public SourceBuildExecutor() {
        this(Map.of(), Runtime.getRuntime().availableProcessors(), List.of("make"), new ProcessCommandExecutor());
    }

    SourceBuildExecutor(
            final Map<String, String> environmentOverrides,
            final int parallelJobs,
            final List<String> makeCommand) {
        this(environmentOverrides, parallelJobs, makeCommand, new ProcessCommandExecutor());
    }

    SourceBuildExecutor(
            final Map<String, String> environmentOverrides,
            final int parallelJobs,
            final List<String> makeCommand,
            final ProcessCommandExecutor processCommandExecutor) {
        this.environmentOverrides = Map.copyOf(Objects.requireNonNull(environmentOverrides, "environmentOverrides"));
        if (parallelJobs <= 0) {
            throw new IllegalArgumentException("parallelJobs must be positive");
        }
        this.parallelJobs = parallelJobs;
        this.makeCommand = List.copyOf(Objects.requireNonNull(makeCommand, "makeCommand"));
        if (this.makeCommand.isEmpty()) {
            throw new IllegalArgumentException("makeCommand must not be empty");
        }
        this.processCommandExecutor = Objects.requireNonNull(processCommandExecutor, "processCommandExecutor");
    }

    @Override
    public Path build(
            final PlatformBuildDriver driver,
            final PostgresRelease release,
            final Path sourceTree,
            final Path buildDirectory) {
        final PlatformBuildDriver validatedDriver = Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(release, "release");
        final Path validatedSourceTree = Objects.requireNonNull(sourceTree, "sourceTree");
        final Path validatedBuildDirectory = Objects.requireNonNull(buildDirectory, "buildDirectory");
        if (validatedDriver instanceof WindowsBuildDriver) {
            throw new UnsupportedOperationException(
                    "source-build driver execution is not implemented yet for "
                            + validatedDriver.targetPlatform().identifier());
        }

        final Path installDirectory = validatedBuildDirectory.resolve("install");
        createDirectories(validatedBuildDirectory, installDirectory);
        final List<String> configureCommand = new ArrayList<>();
        configureCommand.add("/bin/sh");
        configureCommand.add(validatedSourceTree.resolve("configure").toString());
        configureCommand.addAll(validatedDriver.configureArguments(installDirectory));
        runCommand(configureCommand, validatedBuildDirectory);
        runCommand(commandWithArguments("-j" + parallelJobs), validatedBuildDirectory);
        runCommand(commandWithArguments("install-world-bin"), validatedBuildDirectory);
        return installDirectory;
    }

    private List<String> commandWithArguments(final String argument) {
        final List<String> command = new ArrayList<>(makeCommand);
        command.add(argument);
        return command;
    }

    private void runCommand(final List<String> command, final Path workingDirectory) {
        processCommandExecutor.runCommand(
                command,
                workingDirectory,
                environmentOverrides,
                "failed to execute source-build command");
    }

    private static void createDirectories(final Path buildDirectory, final Path installDirectory) {
        try {
            Files.createDirectories(buildDirectory);
            Files.createDirectories(installDirectory);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to prepare source-build workspace", exception);
        }
    }
}
