package eu.virtualparadox.managedpostgres.runtime.packaging.build.execution;

import eu.virtualparadox.managedpostgres.runtime.packaging.build.BuildExecutor;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.PlatformBuildDriver;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.WindowsBuildDriver;
import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Executes PostgreSQL source builds through the upstream MSVC helper scripts.
 */
public final class WindowsBuildExecutor implements BuildExecutor {

    private final Map<String, String> environmentOverrides;
    private final List<String> commandPrefix;
    private final String operatingSystemName;
    private final ProcessCommandExecutor processCommandExecutor;

    /**
     * Creates a Windows build executor using the current process environment.
     */
    public WindowsBuildExecutor() {
        this(Map.of(), List.of("cmd.exe", "/c"), System.getProperty("os.name", ""), new ProcessCommandExecutor());
    }

    WindowsBuildExecutor(
            final Map<String, String> environmentOverrides,
            final List<String> commandPrefix,
            final String operatingSystemName) {
        this(environmentOverrides, commandPrefix, operatingSystemName, new ProcessCommandExecutor());
    }

    WindowsBuildExecutor(
            final Map<String, String> environmentOverrides,
            final List<String> commandPrefix,
            final String operatingSystemName,
            final ProcessCommandExecutor processCommandExecutor) {
        this.environmentOverrides = Map.copyOf(Objects.requireNonNull(environmentOverrides, "environmentOverrides"));
        this.commandPrefix = List.copyOf(Objects.requireNonNull(commandPrefix, "commandPrefix"));
        if (this.commandPrefix.isEmpty()) {
            throw new IllegalArgumentException("commandPrefix must not be empty");
        }
        this.operatingSystemName = Objects.requireNonNull(operatingSystemName, "operatingSystemName");
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
        if (!(validatedDriver instanceof WindowsBuildDriver)) {
            throw new IllegalArgumentException("Windows build executor requires a Windows build driver");
        }
        if (!isWindowsHost()) {
            throw new UnsupportedOperationException(
                    "source-build driver execution for "
                            + validatedDriver.targetPlatform().identifier()
                            + " requires a Windows host");
        }

        final Path installDirectory = validatedBuildDirectory.resolve("install");
        final Path msvcDirectory = validatedSourceTree.resolve("src").resolve("tools").resolve("msvc");
        createDirectories(validatedBuildDirectory, installDirectory, msvcDirectory);
        runCommand(command("build"), msvcDirectory);
        runCommand(command("install", installDirectory.toString()), msvcDirectory);
        return installDirectory;
    }

    private boolean isWindowsHost() {
        return operatingSystemName.toLowerCase(Locale.ROOT).contains("windows");
    }

    private List<String> command(final String... arguments) {
        final List<String> command = new ArrayList<>(commandPrefix);
        command.addAll(List.of(arguments));
        return command;
    }

    private void runCommand(final List<String> command, final Path workingDirectory) {
        processCommandExecutor.runCommand(
                command,
                workingDirectory,
                environmentOverrides,
                "failed to execute Windows source-build command");
    }

    private static void createDirectories(
            final Path buildDirectory,
            final Path installDirectory,
            final Path msvcDirectory) {
        try {
            Files.createDirectories(buildDirectory);
            Files.createDirectories(installDirectory);
            if (!Files.isDirectory(msvcDirectory)) {
                throw new IllegalArgumentException("source tree does not contain src/tools/msvc: " + msvcDirectory);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to prepare Windows source-build workspace", exception);
        }
    }
}
