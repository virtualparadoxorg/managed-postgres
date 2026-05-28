package eu.virtualparadox.managedpostgres.lifecycle.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import eu.virtualparadox.managedpostgres.lifecycle.command.CommandRunner;

public final class PgIsReadyProbeTest {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    private Path temporaryDirectory;

    PgIsReadyProbeTest() {
    }

    @Test
    void pgIsReadySuccessMapsToHealthy() throws IOException {
        final PgIsReadyProbe probe = new PgIsReadyProbe(runtimeWithPgIsReady("printf 'accepting\\n'", 0), new CommandRunner());

        final PostgresProbeResult result = probe.probe(connectionInfo(), COMMAND_TIMEOUT);

        assertThat(result.healthy()).isTrue();
        assertThat(result.summary()).contains("healthy");
    }

    @Test
    void pgIsReadyNonZeroMapsToUnhealthyWithDiagnostic() throws IOException {
        final PgIsReadyProbe probe = new PgIsReadyProbe(
                runtimeWithPgIsReady("printf 'rejecting\\n' >&2", 2),
                new CommandRunner());

        final PostgresProbeResult result = probe.probe(connectionInfo(), COMMAND_TIMEOUT);

        assertThat(result.healthy()).isFalse();
        assertThat(result.summary()).contains("not ready");
        final String report = result.diagnosticReport().renderText();
        assertThat(report).contains("pg_isready").contains("exitCode").contains("2");
    }

    @Test
    void pgIsReadyUsesExeExecutableWhenRuntimeProvidesIt() throws IOException {
        final PgIsReadyProbe probe = new PgIsReadyProbe(
                runtimeWithExecutable("pg_isready.exe", "printf 'accepting\\n'", 0),
                new CommandRunner());

        final PostgresProbeResult result = probe.probe(connectionInfo(), COMMAND_TIMEOUT);

        assertThat(result.healthy()).isTrue();
    }

    @Test
    void pgIsReadyCommandFailureMapsToUnhealthyResult() {
        final PgIsReadyProbe probe = new PgIsReadyProbe(temporaryDirectory.resolve("missing-runtime"), new CommandRunner());

        final PostgresProbeResult result = probe.probe(connectionInfo(), Duration.ofMillis(100));

        assertThat(result.healthy()).isFalse();
        assertThat(result.summary()).contains("could not run");
        assertThat(result.diagnosticReport().renderText()).contains("pg_isready");
    }

    @Test
    void probeValueObjectsRejectBlankSummaryAndServerVersion() {
        assertThatThrownBy(() -> new PostgresProbeResult(true, " ", new DiagnosticReport(List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JdbcProbeSnapshot(Path.of("data"), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Path runtimeWithPgIsReady(final String body, final int exitCode) throws IOException {
        return runtimeWithExecutable("pg_isready", body, exitCode);
    }

    private Path runtimeWithExecutable(final String executableName, final String body, final int exitCode) throws IOException {
        final Path binDirectory = temporaryDirectory.resolve("runtime").resolve("bin");
        Files.createDirectories(binDirectory);
        final Path script = binDirectory.resolve(executableName);
        Files.writeString(script, "#!/bin/sh\n" + body + "\nexit " + exitCode + "\n", StandardCharsets.UTF_8);
        assertThat(script.toFile().setExecutable(true)).isTrue();

        return temporaryDirectory.resolve("runtime");
    }

    private static PostgresConnectionInfo connectionInfo() {
        return new PostgresConnectionInfo(
                "127.0.0.1",
                15432,
                "postgres",
                "postgres",
                Secret.redacted());
    }
}
