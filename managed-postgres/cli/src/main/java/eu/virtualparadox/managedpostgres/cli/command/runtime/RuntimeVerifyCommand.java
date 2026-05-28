package eu.virtualparadox.managedpostgres.cli.command.runtime;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import eu.virtualparadox.managedpostgres.cli.command.CliCommonOptions;
import eu.virtualparadox.managedpostgres.cli.command.output.CliFormatOptions;
import eu.virtualparadox.managedpostgres.cli.command.output.CliOutputFormat;
import eu.virtualparadox.managedpostgres.cli.config.CliManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.cli.config.CliYamlConfigurationLoader;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.internal.runtime.ResolvedRuntime;
import eu.virtualparadox.managedpostgres.internal.runtime.TelemetryRuntimeResolver;
import eu.virtualparadox.managedpostgres.runtime.DefaultRuntimeResolver;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/**
 * Resolves and validates a configured PostgreSQL runtime source.
 */
@Command(
        name = "verify",
        description = "Resolve and verify a PostgreSQL runtime source.",
        sortOptions = false)
public final class RuntimeVerifyCommand implements Callable<Integer> {

    @Mixin
    private final CliCommonOptions commonOptions = new CliCommonOptions();

    @Mixin
    private final CliFormatOptions formatOptions = new CliFormatOptions();

    private final PrintWriter output;
    private final CliYamlConfigurationLoader configurationLoader;
    private final TelemetryRuntimeResolver runtimeResolver;

    /**
     * Creates a runtime verify command.
     *
     * @param output standard command output
     */
    public RuntimeVerifyCommand(final PrintWriter output) {
        this(output, new CliYamlConfigurationLoader(), new DefaultRuntimeResolver());
    }

    RuntimeVerifyCommand(
            final PrintWriter output,
            final CliYamlConfigurationLoader configurationLoader) {
        this(output, configurationLoader, new DefaultRuntimeResolver());
    }

    private RuntimeVerifyCommand(
            final PrintWriter output,
            final CliYamlConfigurationLoader configurationLoader,
            final TelemetryRuntimeResolver runtimeResolver) {
        this.output = Objects.requireNonNull(output, "output");
        this.configurationLoader = Objects.requireNonNull(configurationLoader, "configurationLoader");
        this.runtimeResolver = Objects.requireNonNull(runtimeResolver, "runtimeResolver");
    }

    @Override
    public Integer call() throws Exception {
        final CliManagedPostgresConfiguration configuration = commonOptions.toConfiguration(configurationLoader);
        final ResolvedRuntime resolvedRuntime = resolve(configuration);
        render(configuration.runtimeSource().kind(), resolvedRuntime);

        return CliExitCode.OK.code();
    }

    private ResolvedRuntime resolve(final CliManagedPostgresConfiguration configuration) {
        try {
            return runtimeResolver.resolveWithTelemetry(
                    configuration.runtimeSource(),
                    configuration.postgresqlVersion());
        } catch (final ManagedPostgresException exception) {
            throw exception;
        } catch (final IllegalArgumentException exception) {
            final Path configuredPath = configuration.runtimeSource().existingPath().orElse(null);
            throw new ManagedPostgresException(
                    "runtime verification failed",
                    exception,
                    new DiagnosticReport(List.of(new DiagnosticSection(
                            "runtime-validation",
                            configuredPath == null
                                    ? Map.of("source", configuration.runtimeSource().kind())
                                    : Map.of(
                                            "source", configuration.runtimeSource().kind(),
                                            "path", configuredPath.toAbsolutePath().normalize().toString())))));
        }
    }

    private void render(final String source, final ResolvedRuntime resolvedRuntime) {
        final String checkedSource = Objects.requireNonNull(source, "source");
        final Path runtimeDirectory = Objects.requireNonNull(resolvedRuntime, "resolvedRuntime")
                .runtimeDirectory()
                .toAbsolutePath()
                .normalize();
        final long installMillis = resolvedRuntime.installDuration().toMillis();

        if (CliOutputFormat.JSON.equals(formatOptions.format())) {
            output.println("{"
                    + "\"status\":\"verified\","
                    + "\"source\":\"" + checkedSource + "\","
                    + "\"path\":\"" + runtimeDirectory + "\","
                    + "\"installMillis\":" + installMillis
                    + "}");
        } else {
            output.println("verified");
            output.println("source=" + checkedSource);
            output.println("path=" + runtimeDirectory);
            output.println("installMillis=" + installMillis);
        }
    }
}
