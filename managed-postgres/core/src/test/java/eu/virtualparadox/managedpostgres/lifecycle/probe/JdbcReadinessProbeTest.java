package eu.virtualparadox.managedpostgres.lifecycle.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class JdbcReadinessProbeTest {

    @TempDir
    private Path temporaryDirectory;

    JdbcReadinessProbeTest() {
    }

    @Test
    void jdbcProbeAcceptsMatchingDataDirectoryAndMajorVersion() {
        final Path dataDirectory = Path.of("pgdata").toAbsolutePath().normalize();
        final JdbcReadinessProbe probe = JdbcReadinessProbe.validating(
                ignored -> new JdbcProbeSnapshot(dataDirectory, "16.4"),
                dataDirectory,
                16);

        final PostgresProbeResult result = probe.probe(connectionInfo());

        assertThat(result.healthy()).isTrue();
    }

    @Test
    void jdbcProbeRejectsMismatchedDataDirectory() {
        final JdbcReadinessProbe probe = JdbcReadinessProbe.validating(
                ignored -> new JdbcProbeSnapshot(Path.of("other-data"), "16.4"),
                Path.of("pgdata"),
                16);

        final PostgresProbeResult result = probe.probe(connectionInfo());

        assertThat(result.healthy()).isFalse();
        final String report = result.diagnosticReport().renderText();
        assertThat(report).contains("data-directory");
    }

    @Test
    void jdbcProbeRejectsIncompatibleServerVersion() {
        final Path dataDirectory = Path.of("pgdata").toAbsolutePath().normalize();
        final JdbcReadinessProbe probe = JdbcReadinessProbe.validating(
                ignored -> new JdbcProbeSnapshot(dataDirectory, "15.9"),
                dataDirectory,
                16);

        final PostgresProbeResult result = probe.probe(connectionInfo());

        assertThat(result.healthy()).isFalse();
        final String report = result.diagnosticReport().renderText();
        assertThat(report).contains("server-version").contains("15.9");
    }

    @Test
    void jdbcProbeAcceptsEquivalentRealDataDirectoryPath() throws IOException {
        final Path realDataDirectory = temporaryDirectory.resolve("real-data");
        Files.createDirectories(realDataDirectory);
        final Path linkedDataDirectory = temporaryDirectory.resolve("linked-data");
        Files.createSymbolicLink(linkedDataDirectory, realDataDirectory);
        final JdbcReadinessProbe probe = JdbcReadinessProbe.validating(
                ignored -> new JdbcProbeSnapshot(realDataDirectory, "16.4"),
                linkedDataDirectory,
                16);

        final PostgresProbeResult result = probe.probe(connectionInfo());

        assertThat(result.healthy()).isTrue();
    }

    @Test
    void jdbcProbeRejectsNonNumericServerVersionAndInvalidExpectedMajor() {
        final Path dataDirectory = Path.of("pgdata").toAbsolutePath().normalize();
        final JdbcReadinessProbe probe = JdbcReadinessProbe.validating(
                ignored -> new JdbcProbeSnapshot(dataDirectory, "devel"),
                dataDirectory,
                16);

        assertThat(probe.probe(connectionInfo()).healthy()).isFalse();
        assertThatThrownBy(() -> JdbcReadinessProbe.validating(
                ignored -> new JdbcProbeSnapshot(dataDirectory, "16.4"),
                dataDirectory,
                0))
                .isInstanceOf(IllegalArgumentException.class);
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
