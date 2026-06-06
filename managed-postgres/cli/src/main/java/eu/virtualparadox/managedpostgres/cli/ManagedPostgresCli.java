package eu.virtualparadox.managedpostgres.cli;

import eu.virtualparadox.managedpostgres.cli.command.BackupCommand;
import eu.virtualparadox.managedpostgres.cli.command.CleanupCommand;
import eu.virtualparadox.managedpostgres.cli.command.DestroyCommand;
import eu.virtualparadox.managedpostgres.cli.command.DoctorCommand;
import eu.virtualparadox.managedpostgres.cli.command.RestoreCommand;
import eu.virtualparadox.managedpostgres.cli.command.StartCommand;
import eu.virtualparadox.managedpostgres.cli.command.StatusCommand;
import eu.virtualparadox.managedpostgres.cli.command.StopCommand;
import eu.virtualparadox.managedpostgres.cli.command.lifecycle.RestartCommand;
import eu.virtualparadox.managedpostgres.cli.command.runtime.RuntimeCommand;
import eu.virtualparadox.managedpostgres.cli.command.runtime.RuntimeVerifyCommand;
import eu.virtualparadox.managedpostgres.lifecycle.ManagedPostgresFrameworkVersion;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.ParameterException;

/**
 * Root managed-postgres command line adapter.
 */
@Command(
        name = "managed-postgres",
        mixinStandardHelpOptions = true,
        versionProvider = ManagedPostgresCli.ManagedPostgresVersionProvider.class,
        description = "Manage a local PostgreSQL runtime.",
        sortOptions = false)
public final class ManagedPostgresCli implements Callable<Integer> {

    private final PrintWriter output;
    private final PrintWriter errorOutput;

    /**
     * Creates a command line adapter with explicit output streams.
     *
     * @param output standard command output
     * @param errorOutput error command output
     */
    public ManagedPostgresCli(final PrintWriter output, final PrintWriter errorOutput) {
        this.output = Objects.requireNonNull(output, "output");
        this.errorOutput = Objects.requireNonNull(errorOutput, "errorOutput");
    }

    /**
     * Runs the command line against the supplied writers and returns a documented exit code.
     *
     * <p>This is the testable core of the {@link Main} entry point: it constructs the adapter,
     * executes the arguments, flushes both writers, and returns the exit code without terminating
     * the JVM.
     *
     * @param args command line arguments
     * @param output standard command output
     * @param errorOutput error command output
     * @return command exit code
     */
    static int run(final String[] args, final PrintWriter output, final PrintWriter errorOutput) {
        final int exitCode = new ManagedPostgresCli(output, errorOutput).execute(args);
        output.flush();
        errorOutput.flush();

        return exitCode;
    }

    /**
     * Executes the command line and returns a documented exit code.
     *
     * @param arguments command line arguments
     * @return command exit code
     */
    public int execute(final String... arguments) {
        final CommandLine commandLine = newCommandLine();
        commandLine.setOut(output);
        commandLine.setErr(errorOutput);
        commandLine.setParameterExceptionHandler(this::handleParameterException);
        commandLine.setExecutionExceptionHandler(
                (exception, parsedCommandLine, parseResult) -> new CliExceptionHandler(errorOutput).handle(exception));

        return commandLine.execute(arguments);
    }

    private int handleParameterException(final ParameterException exception, final String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        errorOutput.println(exception.getMessage());

        return CliExitCode.CONFIGURATION_ERROR.code();
    }

    /**
     * Prints root command usage when no subcommand is selected.
     *
     * @return successful exit code
     */
    @Override
    public Integer call() {
        newCommandLine().usage(output);

        return CliExitCode.OK.code();
    }

    private CommandLine newCommandLine() {
        final CommandLine commandLine = new CommandLine(this);
        registerSubcommands(commandLine);

        return commandLine;
    }

    private void registerSubcommands(final CommandLine commandLine) {
        final CommandLine checkedCommandLine = Objects.requireNonNull(commandLine, "commandLine");
        checkedCommandLine.addSubcommand("status", new StatusCommand(output));
        checkedCommandLine.addSubcommand("doctor", new DoctorCommand(output));
        checkedCommandLine.addSubcommand("start", new StartCommand(output));
        checkedCommandLine.addSubcommand("stop", new StopCommand(output));
        checkedCommandLine.addSubcommand("restart", new RestartCommand(output));
        checkedCommandLine.addSubcommand("cleanup", new CleanupCommand(output));
        checkedCommandLine.addSubcommand("destroy", new DestroyCommand(output));
        checkedCommandLine.addSubcommand("backup", new BackupCommand(output));
        checkedCommandLine.addSubcommand("restore", new RestoreCommand(output));
        final CommandLine runtime = checkedCommandLine.addSubcommand("runtime", new RuntimeCommand(output));
        runtime.addSubcommand("verify", new RuntimeVerifyCommand(output));
    }

    /**
     * Provides the managed-postgres CLI version string.
     */
    public static final class ManagedPostgresVersionProvider implements IVersionProvider {

        /**
         * Creates a managed-postgres version provider.
         */
        public ManagedPostgresVersionProvider() {}

        /**
         * Returns command line version lines.
         *
         * @return command line version lines
         */
        @Override
        public String[] getVersion() {
            return new String[] {"managed-postgres " + ManagedPostgresFrameworkVersion.current()};
        }
    }
}
