package eu.virtualparadox.managedpostgres.runtime.packaging.build.execution;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.BuildExecutor;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.PlatformBuildDriver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds PostgreSQL from source with Meson and Ninja on every supported platform and version.
 */
public final class MesonBuildExecutor implements BuildExecutor {

    private final Map<String, String> environmentOverrides;
    private final List<String> mesonCommand;
    private final ProcessCommandExecutor processCommandExecutor;

    /**
     * Creates a Meson build executor using the process environment and the {@code meson} on PATH.
     */
    public MesonBuildExecutor() {
        this(Map.of(), List.of("meson"), new ProcessCommandExecutor());
    }

    MesonBuildExecutor(
            final Map<String, String> environmentOverrides,
            final List<String> mesonCommand,
            final ProcessCommandExecutor processCommandExecutor) {
        this.environmentOverrides = Map.copyOf(Objects.requireNonNull(environmentOverrides, "environmentOverrides"));
        this.mesonCommand = List.copyOf(Objects.requireNonNull(mesonCommand, "mesonCommand"));
        if (this.mesonCommand.isEmpty()) {
            throw new IllegalArgumentException("mesonCommand must not be empty");
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

        final Path installDirectory = validatedBuildDirectory.resolve("install");
        final Path mesonBuildDirectory = validatedBuildDirectory.resolve("meson");
        createDirectories(validatedBuildDirectory, installDirectory);

        runMesonSetup(
                setupCommand(validatedDriver, validatedSourceTree, mesonBuildDirectory, installDirectory),
                validatedSourceTree,
                mesonBuildDirectory,
                validatedBuildDirectory);
        runCommand(mesonCommandWith("compile", "-C", mesonBuildDirectory.toString()), validatedBuildDirectory);
        runCommand(mesonCommandWith("install", "-C", mesonBuildDirectory.toString()), validatedBuildDirectory);
        return installDirectory;
    }

    private List<String> setupCommand(
            final PlatformBuildDriver driver,
            final Path sourceTree,
            final Path mesonBuildDirectory,
            final Path installDirectory) {
        final Set<String> declared = MesonOptionFile.declaredOptions(sourceTree);
        final List<String> command = new ArrayList<>(mesonCommand);
        command.add("setup");
        command.add(mesonBuildDirectory.toString());
        command.add(sourceTree.toString());
        command.add("--prefix=" + installDirectory);
        for (final Map.Entry<String, String> entry :
                driver.mesonFeatureSettings().entrySet()) {
            if (declared.contains(entry.getKey())) {
                command.add("-D" + entry.getKey() + "=" + entry.getValue());
            }
        }
        return command;
    }

    /**
     * Runs {@code meson setup}, recovering once from a "conflicting files" failure.
     *
     * <p>Some PostgreSQL release tarballs (e.g. 16.x) ship pre-generated in-tree files
     * that Meson refuses to build over. We remove exactly those files, wipe the partial
     * build directory, and retry. A second failure propagates.
     */
    private void runMesonSetup(
            final List<String> setupCommand,
            final Path sourceTree,
            final Path mesonBuildDirectory,
            final Path workingDirectory) {
        try {
            runCommand(setupCommand, workingDirectory);
            return;
        } catch (IllegalStateException firstFailure) {
            final String failureMessage = firstFailure.getMessage();
            final List<Path> conflicts = MesonSetupConflictResolver.conflictingFiles(
                    failureMessage == null ? "" : failureMessage, sourceTree);
            if (conflicts.isEmpty()) {
                throw firstFailure;
            }
            MesonSetupConflictResolver.clear(conflicts, mesonBuildDirectory);
        }
        runCommand(setupCommand, workingDirectory);
    }

    private List<String> mesonCommandWith(final String... arguments) {
        final List<String> command = new ArrayList<>(mesonCommand);
        command.addAll(List.of(arguments));
        return command;
    }

    private void runCommand(final List<String> command, final Path workingDirectory) {
        processCommandExecutor.runCommand(
                command, workingDirectory, environmentOverrides, "failed to execute Meson source-build command");
    }

    private static void createDirectories(final Path buildDirectory, final Path installDirectory) {
        try {
            Files.createDirectories(buildDirectory);
            Files.createDirectories(installDirectory);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to prepare Meson source-build workspace", exception);
        }
    }
}
