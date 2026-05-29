package eu.virtualparadox.managedpostgres.runtime.packaging.cli;

import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParameterException;

/**
 * Root command line adapter for the runtime packager.
 */
@Command(name = "runtime-packager", mixinStandardHelpOptions = true, sortOptions = false)
public final class RuntimePackagerMain implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimePackagerMain.class);

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

    /**
     * Process entry point for direct {@code java} invocation.
     *
     * @param args command-line arguments
     */
    public static void main(final String[] args) {
        final int exitCode = execute(
                args,
                loggerPrintWriter(LOGGER::info),
                loggerPrintWriter(LOGGER::error));
        if (exitCode != 0) {
            throw new MainExecutionException(exitCode);
        }
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

    private static PrintWriter loggerPrintWriter(final Consumer<String> sink) {
        return new PrintWriter(new BufferedWriter(new LoggerWriter(sink, StandardCharsets.UTF_8)), true);
    }

    private static final class MainExecutionException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        MainExecutionException(final int exitCode) {
            super("runtime-packager exited with code " + exitCode);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    private static final class LoggerWriter extends Writer {

        private final Consumer<String> sink;
        private final StringBuilder buffer;

        LoggerWriter(final Consumer<String> sink, final java.nio.charset.Charset charset) {
            this.sink = Objects.requireNonNull(sink, "sink");
            Objects.requireNonNull(charset, "charset");
            this.buffer = new StringBuilder();
        }

        @Override
        public void write(final char[] cbuf, final int off, final int len) {
            buffer.append(cbuf, off, len);
            flushCompleteLines();
        }

        @Override
        public void flush() {
            if (!buffer.isEmpty()) {
                sink.accept(buffer.toString());
                buffer.setLength(0);
            }
        }

        @Override
        public void close() {
            flush();
        }

        private void flushCompleteLines() {
            int newlineIndex = buffer.indexOf(System.lineSeparator());
            while (newlineIndex >= 0) {
                sink.accept(buffer.substring(0, newlineIndex));
                buffer.delete(0, newlineIndex + System.lineSeparator().length());
                newlineIndex = buffer.indexOf(System.lineSeparator());
            }
        }
    }
}
