package eu.virtualparadox.managedpostgres.lifecycle.command;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs external PostgreSQL lifecycle commands and captures their output.
 */
public final class CommandRunner {

    /**
     * Creates a command runner.
     */
    public CommandRunner() {
    }

    /**
     * Executes a command request.
     *
     * @param request command request
     * @return captured command result
     */
    public CommandResult run(final CommandRequest request) {
        final CommandRequest checkedRequest = Objects.requireNonNull(request, "request");

        final ExecutorService outputReaders = Executors.newFixedThreadPool(2);
        try {
            return runWithOutputReaders(checkedRequest, outputReaders);
        } catch (final IOException exception) {
            throw commandException("Command failed to start", checkedRequest, exception);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw commandException("Command wait was interrupted", checkedRequest, exception);
        } finally {
            outputReaders.shutdownNow();
        }
    }

    private static CommandResult runWithOutputReaders(
            final CommandRequest request,
            final ExecutorService outputReaders) throws IOException, InterruptedException {
        final Process process = startProcess(request);
        try {
            return awaitProcess(request, outputReaders, process);
        } finally {
            destroyIfAlive(process);
        }
    }

    private static Process startProcess(final CommandRequest request) throws IOException {
        final ProcessBuilder processBuilder = new ProcessBuilder(request.command());
        request.workingDirectory().ifPresent(directory -> processBuilder.directory(directory.toFile()));
        processBuilder.environment().putAll(request.environment());

        return processBuilder.start();
    }

    private static CommandResult awaitProcess(
            final CommandRequest request,
            final ExecutorService outputReaders,
            final Process process) throws InterruptedException {
        final Future<String> stdout = outputReaders.submit(readStream(process.getInputStream()));
        final Future<String> stderr = outputReaders.submit(readStream(process.getErrorStream()));

        try {
            if (!process.waitFor(request.timeout().toNanos(), TimeUnit.NANOSECONDS)) {
                destroyAndWait(process);
                throw commandException("Command timed out", request);
            }

            return new CommandResult(
                    process.exitValue(),
                    awaitOutput(stdout, request),
                    awaitOutput(stderr, request),
                    request.renderedCommand());
        } finally {
            destroyIfAlive(process);
            closeProcessStreams(process);
            cancelIfUnfinished(stdout);
            cancelIfUnfinished(stderr);
        }
    }

    private static void destroyAndWait(final Process process) throws InterruptedException {
        destroyDescendants(process);
        process.destroyForcibly();
        process.waitFor();
    }

    private static void destroyIfAlive(final Process process) {
        if (process.isAlive()) {
            destroyDescendants(process);
            process.destroyForcibly();
        }
    }

    private static void destroyDescendants(final Process process) {
        final List<ProcessHandle> descendants = process.toHandle().descendants().toList();
        descendants.reversed().forEach(ProcessHandle::destroyForcibly);
    }

    private static void closeProcessStreams(final Process process) {
        closeQuietly(process.getInputStream());
        closeQuietly(process.getErrorStream());
        closeQuietly(process.getOutputStream());
    }

    private static void closeQuietly(final Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Cleanup best effort: the command result or lifecycle exception is the useful signal.
        }
    }

    private static void cancelIfUnfinished(final Future<String> output) {
        if (!output.isDone()) {
            output.cancel(true);
        }
    }

    private static Callable<String> readStream(final InputStream stream) {
        return () -> {
            try (InputStream input = stream) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        };
    }

    private static String awaitOutput(final Future<String> output, final CommandRequest request)
            throws InterruptedException {
        try {
            return output.get();
        } catch (final ExecutionException exception) {
            throw commandException("Command output capture failed", request, exception);
        }
    }

    private static ManagedPostgresException commandException(final String message, final CommandRequest request) {
        return new ManagedPostgresException(message, diagnosticReport(request));
    }

    private static ManagedPostgresException commandException(
            final String message,
            final CommandRequest request,
            final Throwable cause) {
        return new ManagedPostgresException(message, cause, diagnosticReport(request));
    }

    private static DiagnosticReport diagnosticReport(final CommandRequest request) {
        return new DiagnosticReport(List.of(new DiagnosticSection(
                "command",
                Map.of(
                        "command", request.renderedCommand(),
                        "timeout", request.timeout().toString()))));
    }
}
