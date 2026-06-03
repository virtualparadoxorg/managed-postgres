package eu.virtualparadox.managedpostgres.scenario.real.support;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRequest;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;
import eu.virtualparadox.managedpostgres.runtime.RuntimeBinaryLocator;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolves real PostgreSQL runtimes for opt-in integration tests.
 */
public final class RealPostgresRuntimeEnvironment {

    private static final Duration VERSION_TIMEOUT = Duration.ofSeconds(5);
    private static final String RUNTIME_PROPERTY = "managed.postgres.realRuntime.path";
    private static final String REQUIRED_PROPERTY = "managed.postgres.realRuntime.required";
    private static final String RUNTIME_ENVIRONMENT = "MANAGED_POSTGRES_REAL_RUNTIME";
    private static final String PATH_ENVIRONMENT = "PATH";
    private static final List<String> REQUIRED_EXECUTABLES =
            List.of("pg_ctl", "initdb", "postgres", "pg_isready", "psql", "pg_dump", "pg_restore");

    private final Supplier<String> propertyRuntimeSupplier;
    private final Supplier<String> environmentRuntimeSupplier;
    private final Supplier<String> pathSupplier;
    private final Supplier<String> requiredSupplier;
    private final Function<List<String>, RuntimeCommandResult> commandRunner;

    /**
     * Creates a runtime environment resolver backed by the current process environment.
     */
    public RealPostgresRuntimeEnvironment() {
        this(
                () -> System.getProperty(RUNTIME_PROPERTY),
                () -> System.getenv(RUNTIME_ENVIRONMENT),
                () -> System.getenv(PATH_ENVIRONMENT),
                () -> System.getProperty(REQUIRED_PROPERTY),
                RealPostgresRuntimeEnvironment::runCommand);
    }

    RealPostgresRuntimeEnvironment(
            final Supplier<String> propertyRuntimeSupplier,
            final Supplier<String> environmentRuntimeSupplier,
            final Supplier<String> pathSupplier,
            final Supplier<String> requiredSupplier,
            final Function<List<String>, RuntimeCommandResult> commandRunner) {
        this.propertyRuntimeSupplier = Objects.requireNonNull(propertyRuntimeSupplier, "propertyRuntimeSupplier");
        this.environmentRuntimeSupplier =
                Objects.requireNonNull(environmentRuntimeSupplier, "environmentRuntimeSupplier");
        this.pathSupplier = Objects.requireNonNull(pathSupplier, "pathSupplier");
        this.requiredSupplier = Objects.requireNonNull(requiredSupplier, "requiredSupplier");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
    }

    /**
     * Resolves the real PostgreSQL runtime or returns empty when optional discovery finds no runtime.
     *
     * @return resolved real PostgreSQL runtime
     */
    public Optional<RealPostgresRuntime> resolve() {
        final Optional<RealPostgresRuntime> runtime = explicitRuntime(propertyRuntimeSupplier.get(), "system property")
                .or(() -> explicitRuntime(environmentRuntimeSupplier.get(), "environment variable"))
                .or(this::pgConfigRuntime)
                .or(this::pathRuntime);
        if (runtime.isEmpty() && required()) {
            throw new AssertionError("Real PostgreSQL runtime is required. Set -D%s=/path/to/postgres or %s."
                    .formatted(RUNTIME_PROPERTY, RUNTIME_ENVIRONMENT));
        }

        return runtime;
    }

    private Optional<RealPostgresRuntime> explicitRuntime(final String configuredValue, final String source) {
        final Optional<RealPostgresRuntime> runtime;
        if (StringUtils.isBlank(configuredValue)) {
            runtime = Optional.empty();
        } else {
            runtime = Optional.of(runtimeFromRoot(runtimeRoot(Path.of(configuredValue)), source));
        }

        return runtime;
    }

    private Optional<RealPostgresRuntime> pgConfigRuntime() {
        return executableOnPath("pg_config")
                .map(pgConfig -> commandRunner.apply(List.of(pgConfig.toString(), "--bindir")))
                .filter(RuntimeCommandResult::successful)
                .map(RuntimeCommandResult::stdout)
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .map(Path::of)
                .map(this::runtimeRoot)
                .map(root -> runtimeFromRoot(root, "pg_config"));
    }

    private Optional<RealPostgresRuntime> pathRuntime() {
        return executableOnPath("pg_ctl")
                .map(Path::getParent)
                .map(this::runtimeRoot)
                .map(root -> runtimeFromRoot(root, "PATH"));
    }

    private RealPostgresRuntime runtimeFromRoot(final Path runtimeRoot, final String source) {
        final Path normalizedRoot = runtimeRoot.toAbsolutePath().normalize();
        requireRuntime(normalizedRoot, source);
        final String version = postgresqlVersion(normalizedRoot);
        final int majorVersion = majorVersion(version);

        return new RealPostgresRuntime(normalizedRoot, version, majorVersion);
    }

    private Path runtimeRoot(final Path configuredPath) {
        final Path normalizedPath = configuredPath.toAbsolutePath().normalize();
        final Path fileName = normalizedPath.getFileName();
        final Path root;
        if (fileName != null && "bin".equals(fileName.toString())) {
            root = normalizedPath.getParent();
        } else {
            root = normalizedPath;
        }

        return Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    private void requireRuntime(final Path runtimeRoot, final String source) {
        if (!Files.isDirectory(runtimeRoot.resolve("bin"))) {
            throw new AssertionError(
                    "Configured PostgreSQL runtime from %s does not contain bin/: %s".formatted(source, runtimeRoot));
        }
        REQUIRED_EXECUTABLES.forEach(executable -> requireExecutable(runtimeRoot, executable, source));
    }

    private static void requireExecutable(final Path runtimeRoot, final String executableName, final String source) {
        final Path executable = RuntimeBinaryLocator.resolveBinary(runtimeRoot, executableName);
        if (!Files.isRegularFile(executable)) {
            throw new AssertionError("PostgreSQL runtime from %s is missing required executable: %s"
                    .formatted(source, runtimeRoot.resolve("bin").resolve(executableName)));
        }
    }

    private String postgresqlVersion(final Path runtimeRoot) {
        final RuntimeCommandResult result = commandRunner.apply(List.of(
                RuntimeBinaryLocator.resolveBinary(runtimeRoot, "postgres").toString(), "--version"));
        if (!result.successful()) {
            throw new AssertionError("Could not run postgres --version for runtime: " + runtimeRoot);
        }

        return parseVersion(result.stdout());
    }

    private static String parseVersion(final String output) {
        final String text = StringUtils.trimToEmpty(output);
        final String prefix = "postgres (PostgreSQL) ";
        if (!text.startsWith(prefix)) {
            throw new AssertionError("Could not parse postgres --version output: " + text);
        }

        return StringUtils.split(StringUtils.substringAfter(text, prefix))[0];
    }

    private static int majorVersion(final String postgresqlVersion) {
        final String firstComponent = StringUtils.substringBefore(postgresqlVersion, ".");
        if (!StringUtils.isNumeric(firstComponent)) {
            throw new AssertionError("Could not parse PostgreSQL major version: " + postgresqlVersion);
        }

        return Integer.parseInt(firstComponent);
    }

    private Optional<Path> executableOnPath(final String executableName) {
        final String pathValue = pathSupplier.get();
        final Optional<Path> executable;
        if (StringUtils.isBlank(pathValue)) {
            executable = Optional.empty();
        } else {
            executable = Arrays.stream(pathValue.split(File.pathSeparator))
                    .filter(StringUtils::isNotBlank)
                    .map(Path::of)
                    .map(path -> path.toAbsolutePath().normalize())
                    .flatMap(path -> resolvePathExecutable(path, executableName).stream())
                    .findFirst();
        }

        return executable;
    }

    private static Optional<Path> resolvePathExecutable(final Path directory, final String executableName) {
        final Path plainExecutable = directory.resolve(executableName);
        final Path windowsExecutable = directory.resolve(executableName + ".exe");
        final Optional<Path> executable;
        if (Files.isRegularFile(plainExecutable)) {
            executable = Optional.of(plainExecutable);
        } else if (Files.isRegularFile(windowsExecutable)) {
            executable = Optional.of(windowsExecutable);
        } else {
            executable = Optional.empty();
        }

        return executable;
    }

    private boolean required() {
        return Boolean.parseBoolean(StringUtils.defaultIfBlank(requiredSupplier.get(), "false"));
    }

    private static RuntimeCommandResult runCommand(final List<String> command) {
        RuntimeCommandResult result;
        try {
            final eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult commandResult =
                    new CommandRunner().run(CommandRequest.of(command, VERSION_TIMEOUT));
            result = new RuntimeCommandResult(commandResult.exitCode(), output(commandResult));
        } catch (final ManagedPostgresException exception) {
            result = new RuntimeCommandResult(1, exceptionMessage(exception));
        }

        return result;
    }

    private static String exceptionMessage(final Exception exception) {
        return Objects.toString(exception.getMessage(), exception.getClass().getSimpleName());
    }

    private static String output(
            final eu.virtualparadox.managedpostgres.lifecycle.command.CommandResult commandResult) {
        return StringUtils.firstNonBlank(commandResult.stdout(), commandResult.stderr(), "");
    }

    record RuntimeCommandResult(int exitCode, String stdout) {

        RuntimeCommandResult {
            Objects.requireNonNull(stdout, "stdout");
        }

        boolean successful() {
            return exitCode == 0;
        }
    }
}
