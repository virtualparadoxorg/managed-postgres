package eu.virtualparadox.managedpostgres.cli.command.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.cli.CliExceptionHandler;
import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import eu.virtualparadox.managedpostgres.cli.config.CliYamlConfigurationLoader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class RuntimeVerifyCommandTest {

    @TempDir
    private Path temporaryDirectory;

    RuntimeVerifyCommandTest() {
    }

    @Test
    void verifyExistingRuntimePrintsResolvedPath() throws IOException {
        final Path runtimeDirectory = usableRuntimeDirectory();

        final CliRun run = run("--runtime-existing", runtimeDirectory.toString());

        assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
        assertThat(run.output())
                .contains("verified")
                .contains("source=existing")
                .contains("path=" + runtimeDirectory.toAbsolutePath().normalize());
        assertThat(run.errorOutput()).isEmpty();
    }

    @Test
    void verifyExistingRuntimeRendersJson() throws IOException {
        final Path runtimeDirectory = usableRuntimeDirectory();

        final CliRun run = run("--runtime-existing", runtimeDirectory.toString(), "--format", "json");

        assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
        assertThat(run.output())
                .contains("\"status\":\"verified\"")
                .contains("\"source\":\"existing\"")
                .contains("\"path\":\"" + runtimeDirectory.toAbsolutePath().normalize() + "\"");
    }

    @Test
    void invalidRuntimeMapsToRuntimeExitCode() {
        final Path missingRuntime = temporaryDirectory.resolve("missing-runtime");

        final CliRun run = run("--runtime-existing", missingRuntime.toString());

        assertThat(run.exitCode()).isEqualTo(CliExitCode.RUNTIME_ERROR.code());
        assertThat(run.errorOutput()).contains("runtime-validation");
    }

    private Path usableRuntimeDirectory() throws IOException {
        final Path runtimeDirectory = temporaryDirectory.resolve("postgres");
        final Path binDirectory = runtimeDirectory.resolve("bin");
        Files.createDirectories(binDirectory);
        Files.createFile(binDirectory.resolve("pg_ctl"));
        Files.createFile(binDirectory.resolve("psql"));
        Files.createFile(binDirectory.resolve("postgres"));

        return runtimeDirectory;
    }

    private static CliRun run(final String... arguments) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        final CliRun run;

        try (PrintWriter outputWriter = writer(output);
                PrintWriter errorWriter = writer(errorOutput)) {
            final CommandLine commandLine = new CommandLine(new RuntimeVerifyCommand(
                    outputWriter,
                    new CliYamlConfigurationLoader()));
            commandLine.setOut(outputWriter);
            commandLine.setErr(errorWriter);
            commandLine.setParameterExceptionHandler((exception, commandArguments) -> {
                errorWriter.println(exception.getMessage());
                return CliExitCode.CONFIGURATION_ERROR.code();
            });
            commandLine.setExecutionExceptionHandler((exception, parsedCommandLine, parseResult) ->
                    new CliExceptionHandler(errorWriter).handle(exception));
            final int exitCode = commandLine.execute(arguments);

            outputWriter.flush();
            errorWriter.flush();
            run = new CliRun(exitCode, text(output), text(errorOutput));
        }

        return run;
    }

    private static PrintWriter writer(final ByteArrayOutputStream output) {
        return new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
    }

    private static String text(final ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }

    private record CliRun(int exitCode, String output, String errorOutput) {
    }
}
