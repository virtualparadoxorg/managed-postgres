package eu.virtualparadox.managedpostgres.runtime.packaging.cli;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParameterException;

/**
 * Root command line adapter for the runtime packager.
 */
@Command(name = "runtime-packager", mixinStandardHelpOptions = true, sortOptions = false)
public final class RuntimePackagerMain implements Callable<Integer> {

    private final PrintWriter output;
    private final PrintWriter errorOutput;

    /**
     * Creates a runtime packager command line adapter.
     *
     * @param output standard command output
     * @param errorOutput standard command error output
     */
    public RuntimePackagerMain(final PrintWriter output, final PrintWriter errorOutput) {
        this.output = Objects.requireNonNull(output, "output");
        this.errorOutput = Objects.requireNonNull(errorOutput, "errorOutput");
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
        commandLine.setExecutionExceptionHandler((exception, parsedCommandLine, parseResult) -> {
            errorOutput.println(exception.getMessage());
            return parsedCommandLine.getCommandSpec().exitCodeOnExecutionException();
        });
        return commandLine.execute(arguments);
    }

    /**
     * Executes the runtime packager CLI against custom writers.
     *
     * @param args command-line arguments
     * @param output standard output sink
     * @param error standard error sink
     * @return process-style exit code
     */
    public static int execute(final String[] args, final PrintWriter output, final PrintWriter error) {
        return new RuntimePackagerMain(output, error).execute(args);
    }

    @Override
    public Integer call() {
        newCommandLine().usage(output);
        return 0;
    }

    private int handleParameterException(final ParameterException exception, final String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        errorOutput.println(exception.getMessage());
        newCommandLine().usage(errorOutput);
        return 2;
    }

    private CommandLine newCommandLine() {
        final CommandLine commandLine = new CommandLine(this);
        commandLine.addSubcommand("package", new PackageRuntimeCommand(output, errorOutput));
        return commandLine;
    }
}
