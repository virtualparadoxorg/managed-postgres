package eu.virtualparadox.managedpostgres.cli;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Standalone process entry point for the managed-postgres command line.
 *
 * <p>This is the {@code Main-Class} of the executable jar. It only adapts the process streams and
 * exit code; all behaviour lives in {@link ManagedPostgresCli#run(String[], PrintWriter,
 * PrintWriter)}, which is exercised by the test suite.
 */
public final class Main {

    private Main() {}

    /**
     * Builds UTF-8 auto-flushing writers over the process streams, delegates to {@link
     * ManagedPostgresCli#run(String[], PrintWriter, PrintWriter)}, and terminates the JVM with the
     * resulting exit code.
     *
     * @param args command line arguments
     */
    @SuppressWarnings("PMD.CloseResource") // Writers wrap the long-lived process streams; closing them would discard
    // later output and is forbidden by ErrorProne ClosingStandardOutputStreams. run(...) flushes them instead.
    public static void main(final String[] args) {
        final PrintWriter output =
                new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8)), true);
        final PrintWriter errorOutput =
                new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8)), true);

        System.exit(ManagedPostgresCli.run(args, output, errorOutput));
    }
}
