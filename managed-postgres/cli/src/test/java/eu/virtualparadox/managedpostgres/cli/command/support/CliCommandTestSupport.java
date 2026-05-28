package eu.virtualparadox.managedpostgres.cli.command.support;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.cli.CliExceptionHandler;
import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import eu.virtualparadox.managedpostgres.cli.command.BackupCommand;
import eu.virtualparadox.managedpostgres.cli.command.DoctorCommand;
import eu.virtualparadox.managedpostgres.cli.command.RestoreCommand;
import eu.virtualparadox.managedpostgres.cli.command.StartCommand;
import eu.virtualparadox.managedpostgres.cli.command.StatusCommand;
import eu.virtualparadox.managedpostgres.cli.command.StopCommand;
import eu.virtualparadox.managedpostgres.cli.command.CleanupCommand;
import eu.virtualparadox.managedpostgres.cli.command.DestroyCommand;
import eu.virtualparadox.managedpostgres.cli.config.CliManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.cli.config.CliYamlConfigurationLoader;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

public final class CliCommandTestSupport {

    private CliCommandTestSupport() {
    }

    public static CliRun runStatus(final TestManagedPostgres postgres, final String... arguments) {
        final CommandFactory factory = outputWriter -> new StatusCommand(
                outputWriter,
                configuration -> postgres,
                new CliYamlConfigurationLoader());

        return run(factory, arguments);
    }

    public static CliRun runDoctor(final TestManagedPostgres postgres, final String... arguments) {
        final CommandFactory factory = outputWriter -> new DoctorCommand(
                outputWriter,
                configuration -> postgres,
                new CliYamlConfigurationLoader());

        return run(factory, arguments);
    }

    public static CliRun runStart(final TestManagedPostgres postgres, final String... arguments) {
        final AtomicReference<CliManagedPostgresConfiguration> configuration = new AtomicReference<>();
        final CommandFactory factory = outputWriter -> new StartCommand(
                outputWriter,
                value -> {
                    configuration.set(value);
                    return postgres;
                },
                new CliYamlConfigurationLoader());

        return run(factory, configuration, arguments);
    }

    public static CliRun runStop(final TestManagedPostgres postgres, final String... arguments) {
        final CommandFactory factory = outputWriter -> new StopCommand(
                outputWriter,
                configuration -> postgres,
                new CliYamlConfigurationLoader());

        return run(factory, arguments);
    }

    public static CliRun runBackup(final TestManagedPostgres postgres, final String... arguments) {
        final CommandFactory factory = outputWriter -> new BackupCommand(
                outputWriter,
                configuration -> postgres,
                new CliYamlConfigurationLoader());

        return run(factory, arguments);
    }

    public static CliRun runRestore(final TestManagedPostgres postgres, final String... arguments) {
        final CommandFactory factory = outputWriter -> new RestoreCommand(
                outputWriter,
                configuration -> postgres,
                new CliYamlConfigurationLoader());

        return run(factory, arguments);
    }

    public static CliRun runCleanup(final TestManagedPostgres postgres, final String... arguments) {
        final CommandFactory factory = outputWriter -> new CleanupCommand(
                outputWriter,
                configuration -> postgres,
                new CliYamlConfigurationLoader());

        return run(factory, arguments);
    }

    public static CliRun runDestroy(final TestManagedPostgres postgres, final String... arguments) {
        final CommandFactory factory = outputWriter -> new DestroyCommand(
                outputWriter,
                configuration -> postgres,
                new CliYamlConfigurationLoader());

        return run(factory, arguments);
    }

    private static CliRun run(final CommandFactory commandFactory, final String... arguments) {
        return run(commandFactory, new AtomicReference<>(), arguments);
    }

    private static CliRun run(
            final CommandFactory commandFactory,
            final AtomicReference<CliManagedPostgresConfiguration> configuration,
            final String... arguments) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        final CliRun run;

        try (PrintWriter outputWriter = writer(output);
                PrintWriter errorWriter = writer(errorOutput)) {
            final CommandLine commandLine = new CommandLine(commandFactory.create(outputWriter));
            commandLine.setOut(outputWriter);
            commandLine.setErr(errorWriter);
            commandLine.setParameterExceptionHandler((exception, commandArguments) ->
                    handleParameterException(errorWriter, exception, commandArguments));
            commandLine.setExecutionExceptionHandler((exception, parsedCommandLine, parseResult) ->
                    new CliExceptionHandler(errorWriter).handle(exception));
            final int exitCode = commandLine.execute(arguments);

            outputWriter.flush();
            errorWriter.flush();
            run = new CliRun(exitCode, text(output), text(errorOutput), Optional.ofNullable(configuration.get()));
        }

        return run;
    }

    private static int handleParameterException(
            final PrintWriter errorWriter,
            final ParameterException exception,
            final String[] arguments) {
        assertThat(arguments).isNotNull();
        errorWriter.println(exception.getMessage());

        return CliExitCode.CONFIGURATION_ERROR.code();
    }

    private static PrintWriter writer(final ByteArrayOutputStream output) {
        return new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
    }

    private static String text(final ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface CommandFactory {

        Object create(PrintWriter outputWriter);
    }

    public record CliRun(
            int exitCode,
            String output,
            String errorOutput,
            Optional<CliManagedPostgresConfiguration> configuration) {
    }
}
