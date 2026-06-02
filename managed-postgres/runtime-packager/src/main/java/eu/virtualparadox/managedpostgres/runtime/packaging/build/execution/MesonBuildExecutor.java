package eu.virtualparadox.managedpostgres.runtime.packaging.build.execution;

import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.BuildExecutor;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.PlatformBuildDriver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds PostgreSQL from source with Meson and Ninja on every supported platform and version.
 */
public final class MesonBuildExecutor implements BuildExecutor {

    private static final Pattern OPTION_NAME = Pattern.compile("option\\(\\s*'([^']+)'");
    private static final List<String> OPTION_FILE_NAMES = List.of("meson.options", "meson_options.txt");

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

        runCommand(setupCommand(validatedDriver, validatedSourceTree, mesonBuildDirectory, installDirectory),
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
        final Set<String> declared = declaredOptions(sourceTree);
        final List<String> command = new ArrayList<>(mesonCommand);
        command.add("setup");
        command.add(mesonBuildDirectory.toString());
        command.add(sourceTree.toString());
        command.add("--prefix=" + installDirectory);
        for (final Map.Entry<String, String> entry : driver.mesonFeatureSettings().entrySet()) {
            if (declared.contains(entry.getKey())) {
                command.add("-D" + entry.getKey() + "=" + entry.getValue());
            }
        }
        return command;
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

    private static Set<String> declaredOptions(final Path sourceTree) {
        for (final String fileName : OPTION_FILE_NAMES) {
            final Path optionFile = sourceTree.resolve(fileName);
            if (Files.isRegularFile(optionFile)) {
                return parseOptionNames(optionFile);
            }
        }
        throw new IllegalStateException("source tree has no meson option file: " + sourceTree);
    }

    private static Set<String> parseOptionNames(final Path optionFile) {
        final Set<String> names = new LinkedHashSet<>();
        try {
            for (final String line : Files.readAllLines(optionFile, StandardCharsets.UTF_8)) {
                final Matcher matcher = OPTION_NAME.matcher(line);
                if (matcher.find()) {
                    names.add(matcher.group(1));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read meson option file: " + optionFile, exception);
        }
        return names;
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
