package eu.virtualparadox.managedpostgres.cli.command.runtime;

import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Groups runtime-specific CLI operations.
 */
@Command(name = "runtime", description = "Runtime source operations.", sortOptions = false)
public final class RuntimeCommand implements Callable<Integer> {

    private final PrintWriter output;

    /**
     * Creates a runtime command group.
     *
     * @param output standard command output
     */
    public RuntimeCommand(final PrintWriter output) {
        this.output = Objects.requireNonNull(output, "output");
    }

    @Override
    public Integer call() {
        final CommandLine commandLine = new CommandLine(this);
        commandLine.addSubcommand("verify", new RuntimeVerifyCommand(output));
        commandLine.usage(output);

        return CliExitCode.OK.code();
    }
}
