package eu.virtualparadox.managedpostgres.scenario.cli;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import eu.virtualparadox.managedpostgres.cli.ManagedPostgresCli;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.scenario.support.LoopbackTcpServer;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioJdbcDriver;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioMetadata;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioShell;
import eu.virtualparadox.managedpostgres.test.FakePostgresRuntime;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("driver-manager")
final class FakeRuntimeCliIT {

    private static final String APP_PASSWORD = "app-password";
    private static final String TEST_PASSWORD = "test-password";

    @TempDir
    private Path temporaryDirectory;

    FakeRuntimeCliIT() {
    }

    @Test
    void cliWorkflowCoversPersistentLifecycleDiagnosticsBackupAndStop() throws IOException, SQLException {
        final Path commandLog = temporaryDirectory.resolve("postgres-command-calls.log");
        final FakePostgresRuntime runtime = runtime(commandLog);
        final Path storageRoot = temporaryDirectory.resolve("cluster");
        final Path config = writeConfig(storageRoot, runtime);
        final Path backup = temporaryDirectory.resolve("backups").resolve("app.dump");

        assertSuccessful(runWithConfig(config, "start", "--keep-running"));
        assertThat(ScenarioMetadata.metadataPath(storageRoot)).isRegularFile();
        assertThat(Files.readAllLines(commandLog)).contains("start");

        final PostgresInstanceMetadata metadata = ScenarioMetadata.require(storageRoot);
        try (LoopbackTcpServer server = LoopbackTcpServer.open(metadata.host(), metadata.port());
                ScenarioJdbcDriver driver = ScenarioJdbcDriver.register(metadata)) {
            assertStatusIsRunning(runWithConfig(config, "status", "--format", "json"));
            assertDoctorJsonIsRedacted(runWithConfig(config, "doctor", "--format", "json"));
            assertSuccessful(runWithConfig(config, "backup", backup.toString()));
            assertThat(startCalls(commandLog)).hasSize(1);
            assertRestoreWithoutSafetyFlagsIsRejected(runWithConfig(config, "restore", backup.toString()));
            server.assertHealthy();
            assertThat(driver.connections()).isPositive();
        }

        assertBackupArtifacts(backup);
        assertSuccessful(runWithConfig(config, "stop"));
        assertThat(Files.readAllLines(commandLog)).contains("stop");
        assertThat(ScenarioMetadata.read(storageRoot)).isEmpty();
        assertThat(ScenarioMetadata.readPort(storageRoot)).hasValue(metadata.port());
    }

    private FakePostgresRuntime runtime(final Path commandLog) throws IOException {
        return FakePostgresRuntime.create(
                temporaryDirectory.resolve("runtime"),
                ScenarioShell.recordingPgCtl(commandLog),
                ScenarioShell.recordingBootstrapPsql(commandLog),
                ScenarioShell.recordingPgDump(commandLog),
                ScenarioShell.recordingPgRestore(commandLog));
    }

    private Path writeConfig(
            final Path storageRoot,
            final FakePostgresRuntime runtime) throws IOException {
        final Path config = temporaryDirectory.resolve("managed-postgres.yml");
        Files.writeString(config, String.join(System.lineSeparator(),
                "managed-postgres:",
                "  name: app-db",
                "  version: \"16.4\"",
                "  storage:",
                "    path: " + storageRoot,
                "  runtime:",
                "    source: existing",
                "    path: " + runtime.runtimeDirectory(),
                ""), StandardCharsets.UTF_8);

        return config;
    }

    private static CliRun runWithConfig(
            final Path config,
            final String command,
            final String... commandArguments) {
        final List<String> arguments = Stream.concat(
                        Stream.of(command, "--config", config.toString()),
                        Arrays.stream(commandArguments))
                .toList();

        return run(arguments.toArray(String[]::new));
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

    private static void assertSuccessful(final CliRun run) {
        assertThat(run.exitCode()).as(run.errorOutput()).isEqualTo(CliExitCode.OK.code());
        assertNoSecrets(run);
    }

    private static void assertStatusIsRunning(final CliRun run) {
        assertSuccessful(run);
        assertThat(run.output()).contains("{\"status\":\"RUNNING\"}");
    }

    private static void assertDoctorJsonIsRedacted(final CliRun run) {
        assertSuccessful(run);
        assertThat(run.output())
                .startsWith("{")
                .contains("\"status\": \"RUNNING\"")
                .contains("\"sections\"")
                .contains("\"probes\"")
                .endsWith("}" + System.lineSeparator());
    }

    private static void assertRestoreWithoutSafetyFlagsIsRejected(final CliRun run) {
        assertThat(run.exitCode()).isEqualTo(CliExitCode.CONFIGURATION_ERROR.code());
        assertThat(run.errorOutput()).contains("--drop-current-database");
        assertNoSecrets(run);
    }

    private static void assertBackupArtifacts(final Path backup) {
        assertThat(backup).hasContent("fake dump\n");
        assertThat(manifestPath(backup)).isRegularFile();
        assertThat(checksumPath(backup)).isRegularFile();
    }

    private static List<String> startCalls(final Path commandLog) throws IOException {
        return Files.readAllLines(commandLog).stream()
                .filter("start"::equals)
                .toList();
    }

    private static void assertNoSecrets(final CliRun run) {
        assertThat(run.output()).doesNotContain(APP_PASSWORD, TEST_PASSWORD);
        assertThat(run.errorOutput()).doesNotContain(APP_PASSWORD, TEST_PASSWORD);
    }

    private static Path manifestPath(final Path target) {
        return Path.of(target + ".manifest.json");
    }

    private static Path checksumPath(final Path target) {
        return Path.of(target + ".sha256");
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
