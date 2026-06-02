package eu.virtualparadox.managedpostgres.runtime.packaging.build.execution;

import eu.virtualparadox.managedpostgres.runtime.packaging.build.BuildExecutor;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.PlatformBuildDriver;
import eu.virtualparadox.managedpostgres.runtime.packaging.build.WindowsBuildDriver;
import eu.virtualparadox.managedpostgres.runtime.packaging.PostgresRelease;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Executes PostgreSQL source builds through the upstream MSVC helper scripts.
 */
public final class WindowsBuildExecutor implements BuildExecutor {

    private static final String WINDOWS_DIAGNOSTICS_ENVIRONMENT_NAME =
            "MANAGED_POSTGRES_WINDOWS_DIAGNOSTICS";
    private static final String WINDOWS_DIAGNOSTICS_SCRIPT_NAME =
            "managed-postgres-windows-env-probe.cmd";
    private static final String WINDOWS_DIAGNOSTICS_OUTPUT_NAME =
            "managed-postgres-windows-env-probe.txt";
    private static final String WINDOWS_BUILDENV_FILE_NAME = "buildenv.pl";
    private static final String WINDOWS_BUILD_SCRIPT_NAME = "build.pl";
    private static final String WINDOWS_INSTALL_SCRIPT_NAME = "install.pl";

    private final Map<String, String> environmentOverrides;
    private final Map<String, String> processEnvironment;
    private final List<String> commandPrefix;
    private final String operatingSystemName;
    private final ProcessCommandExecutor processCommandExecutor;
    private final WindowsPathToolResolver windowsPathToolResolver;

    /**
     * Creates a Windows build executor using the current process environment.
     */
    public WindowsBuildExecutor() {
        this(
                Map.of(),
                System.getenv(),
                List.of("cmd.exe", "/c"),
                System.getProperty("os.name", ""),
                new ProcessCommandExecutor());
    }

    WindowsBuildExecutor(
            final Map<String, String> environmentOverrides,
            final List<String> commandPrefix,
            final String operatingSystemName) {
        this(
                environmentOverrides,
                System.getenv(),
                commandPrefix,
                operatingSystemName,
                new ProcessCommandExecutor());
    }

    WindowsBuildExecutor(
            final Map<String, String> environmentOverrides,
            final Map<String, String> processEnvironment,
            final List<String> commandPrefix,
            final String operatingSystemName,
            final ProcessCommandExecutor processCommandExecutor) {
        this.environmentOverrides = normalizeEnvironmentOverrides(environmentOverrides);
        this.processEnvironment = Map.copyOf(Objects.requireNonNull(processEnvironment, "processEnvironment"));
        this.commandPrefix = List.copyOf(Objects.requireNonNull(commandPrefix, "commandPrefix"));
        if (this.commandPrefix.isEmpty()) {
            throw new IllegalArgumentException("commandPrefix must not be empty");
        }
        this.operatingSystemName = Objects.requireNonNull(operatingSystemName, "operatingSystemName");
        this.processCommandExecutor = Objects.requireNonNull(processCommandExecutor, "processCommandExecutor");
        this.windowsPathToolResolver = new WindowsPathToolResolver(this.environmentOverrides, this.processEnvironment);
    }

    static Map<String, String> normalizeEnvironmentOverrides(final Map<String, String> environmentOverrides) {
        final Map<String, String> normalizedOverrides =
                new java.util.LinkedHashMap<>(Objects.requireNonNull(environmentOverrides, "environmentOverrides"));
        final String upperPath = normalizedOverrides.get("PATH");
        final String windowsPath = normalizedOverrides.get("Path");
        if (upperPath != null && windowsPath == null) {
            normalizedOverrides.put("Path", upperPath);
        }
        if (windowsPath != null && upperPath == null) {
            normalizedOverrides.put("PATH", windowsPath);
        }
        return Map.copyOf(normalizedOverrides);
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
        final Path perlExecutable = windowsPathToolResolver.resolvePreferredPerlExecutable()
                .orElseThrow(() -> new IllegalStateException("unable to resolve a Windows perl.exe from PATH"));
        writeBuildEnvironment(msvcDirectory, perlExecutable);
        maybeCaptureWindowsDiagnostics(validatedBuildDirectory, msvcDirectory);
        runCommand(command(perlExecutable.toString(), WINDOWS_BUILD_SCRIPT_NAME), msvcDirectory);
        runCommand(
                command(perlExecutable.toString(), WINDOWS_INSTALL_SCRIPT_NAME, installDirectory.toString()),
                msvcDirectory);
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

    private void maybeCaptureWindowsDiagnostics(final Path buildDirectory, final Path msvcDirectory) {
        if (!diagnosticsEnabled(environmentOverrides, processEnvironment)) {
            return;
        }
        final Path diagnosticsScript = buildDirectory.resolve(WINDOWS_DIAGNOSTICS_SCRIPT_NAME);
        final Path diagnosticsOutput = buildDirectory.resolve(WINDOWS_DIAGNOSTICS_OUTPUT_NAME);
        writeWindowsDiagnosticsScript(diagnosticsScript, diagnosticsOutput);
        runCommand(command(diagnosticsScript.toString()), msvcDirectory);
    }

    private void runCommand(final List<String> command, final Path workingDirectory) {
        processCommandExecutor.runCommand(
                command,
                workingDirectory,
                environmentOverrides,
                "failed to execute Windows source-build command");
    }

    private void writeBuildEnvironment(final Path msvcDirectory, final Path perlExecutable) {
        final List<String> prependedDirectories = new ArrayList<>();
        // Place the native Windows perl directory first so the nested `perl` invocations
        // emitted into the generated MSVC project files (gendef.pl, pgbison.pl, pgflex.pl)
        // resolve to Strawberry perl rather than Git's MSYS perl, whose system() routes
        // through /bin/sh and strips backslashes from Windows paths (e.g. turning the
        // tempdir "Release\postgres" into "Releasepostgres").
        final Path perlDirectory = Objects.requireNonNull(perlExecutable.getParent(), "perlExecutable.parent");
        prependedDirectories.add(perlDirectory.toString());
        windowsPathToolResolver.resolveExecutableOnPath("MSBuild.exe")
                .map(Path::getParent)
                .map(Path::toString)
                .ifPresent(prependedDirectories::add);
        final Path buildEnvironmentFile = msvcDirectory.resolve(WINDOWS_BUILDENV_FILE_NAME);
        writeBuildEnvironmentFile(buildEnvironmentFile, prependedDirectories);
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

    static boolean diagnosticsEnabled(final Map<String, String> environmentOverrides) {
        return diagnosticsEnabled(environmentOverrides, Map.of());
    }

    static boolean diagnosticsEnabled(
            final Map<String, String> environmentOverrides,
            final Map<String, String> processEnvironment) {
        final Map<String, String> validatedOverrides =
                Objects.requireNonNull(environmentOverrides, "environmentOverrides");
        final Map<String, String> validatedProcessEnvironment =
                Objects.requireNonNull(processEnvironment, "processEnvironment");
        final String overrideValue = validatedOverrides.get(WINDOWS_DIAGNOSTICS_ENVIRONMENT_NAME);
        final String configuredValue = overrideValue != null
                ? overrideValue
                : validatedProcessEnvironment.get(WINDOWS_DIAGNOSTICS_ENVIRONMENT_NAME);
        final boolean enabled = "1".equals(configuredValue);
        return enabled;
    }

    static String buildEnvironmentScriptContent(final List<String> prependedDirectories) {
        final String joinedDirectories =
                Objects.requireNonNull(prependedDirectories, "prependedDirectories").stream()
                        .map(directory -> Objects.requireNonNull(directory, "directory").replace("\\", "/"))
                        .collect(Collectors.joining(";"));
        return String.format(Locale.ROOT, "$ENV{PATH} = \"%s;$ENV{PATH}\";%n", joinedDirectories);
    }

    static String windowsDiagnosticsScriptContent(final String diagnosticsOutputPath) {
        Objects.requireNonNull(diagnosticsOutputPath, "diagnosticsOutputPath");
        return String.format(
                Locale.ROOT,
                "@echo off%n"
                        + "setlocal%n"
                        + "> \"%1$s\" echo PATH=%%PATH%%%n"
                        + ">> \"%1$s\" echo Path=%%Path%%%n"
                        + ">> \"%1$s\" echo PATHEXT=%%PATHEXT%%%n"
                        + ">> \"%1$s\" where msbuild 2^>^&1%n"
                        + ">> \"%1$s\" where cl 2^>^&1%n"
                        + ">> \"%1$s\" where link 2^>^&1%n",
                diagnosticsOutputPath);
    }

    private static void writeWindowsDiagnosticsScript(
            final Path diagnosticsScriptPath,
            final Path diagnosticsOutputPath) {
        try {
            Files.createDirectories(
                    Objects.requireNonNull(diagnosticsScriptPath.getParent(), "diagnosticsScriptPath.parent"));
            Files.writeString(
                    diagnosticsScriptPath,
                    windowsDiagnosticsScriptContent(diagnosticsOutputPath.toString()),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to write Windows diagnostics script", exception);
        }
    }

    private static void writeBuildEnvironmentFile(
            final Path buildEnvironmentFile, final List<String> prependedDirectories) {
        try {
            Files.writeString(
                    buildEnvironmentFile,
                    buildEnvironmentScriptContent(prependedDirectories),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to write Windows build environment file", exception);
        }
    }
}
