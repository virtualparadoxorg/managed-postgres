package eu.virtualparadox.managedpostgres.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class ManagedPostgresCliTest {

    ManagedPostgresCliTest() {
    }

    @Test
    void noArgumentsReturnSuccessAndPrintUsage() {
        final CliRun run = run();

        assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
        assertThat(run.output()).contains("Usage: managed-postgres");
        assertThat(run.errorOutput()).isEmpty();
    }

    @Test
    void helpReturnsSuccessAndPrintsUsage() {
        final CliRun run = run("--help");

        assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
        assertThat(run.output()).contains("Usage: managed-postgres");
        assertThat(run.errorOutput()).isEmpty();
    }

    @Test
    void versionReturnsSuccessAndPrintsFrameworkVersion() {
        final CliRun run = run("--version");

        assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
        assertThat(run.output()).contains("managed-postgres");
        assertThat(run.errorOutput()).isEmpty();
    }

    @Test
    void unknownCommandReturnsConfigurationError() {
        final CliRun run = run("missing-command");

        assertThat(run.exitCode()).isEqualTo(CliExitCode.CONFIGURATION_ERROR.code());
        assertThat(run.errorOutput()).contains("Unmatched argument");
    }

    @Test
    void rootHelpDoesNotPrintInternalImplementationTypes() {
        final CliRun run = run("--help");

        assertThat(run.output())
                .doesNotContain("ProcessBuilder")
                .doesNotContain("ProcessHandle")
                .doesNotContain("Process ")
                .doesNotContain("Platform")
                .doesNotContain("OperatingSystem")
                .doesNotContain("CpuArchitecture")
                .doesNotContain("LibcVariant");
    }

    @Test
    void rootHelpListsCleanupAndDestroyCommands() {
        final CliRun run = run("--help");

        assertThat(run.output()).contains("cleanup").contains("destroy");
    }

    private static CliRun run(final String... arguments) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        final CliRun run;

        try (PrintWriter outputWriter = writer(output);
                PrintWriter errorWriter = writer(errorOutput)) {
            final ManagedPostgresCli cli = new ManagedPostgresCli(outputWriter, errorWriter);
            final int exitCode = cli.execute(arguments);

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
