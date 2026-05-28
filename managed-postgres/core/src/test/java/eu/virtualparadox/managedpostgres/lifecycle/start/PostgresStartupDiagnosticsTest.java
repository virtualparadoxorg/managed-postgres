package eu.virtualparadox.managedpostgres.lifecycle.start;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.exception.PostgresStartupException;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresLayoutFixture;
import eu.virtualparadox.managedpostgres.lifecycle.probe.PostgresProbeResult;
import eu.virtualparadox.managedpostgres.lifecycle.PostgresStartupDiagnostics;

public final class PostgresStartupDiagnosticsTest {

    @TempDir
    private Path temporaryDirectory;

    PostgresStartupDiagnosticsTest() {
    }

    @Test
    void startupFailureReturnsExistingStartupException() {
        final PostgresStartupException existing = new PostgresStartupException(
                "already classified",
                PostgresStartupDiagnostics.diagnostic("existing", Map.of("key", "value")));

        final PostgresStartupException result = PostgresStartupDiagnostics.startupFailure(
                "wrapper",
                existing,
                "ignored",
                Map.of("ignored", "value"));

        assertThat(result).isSameAs(existing);
    }

    @Test
    void commandFailureKeepsNestedDiagnosticsAndExceptionMessage() {
        final ManagedPostgresException cause = new DiagnosticManagedPostgresException(new DiagnosticReport(List.of(
                new DiagnosticSection("nested", Map.of("path", "postgres.log")))));

        final PostgresStartupException result = PostgresStartupDiagnostics.commandFailure(
                "PostgreSQL command failed",
                "pg_ctl",
                cause);

        assertThat(result.diagnosticReport().renderText())
                .contains("pg_ctl")
                .contains("diagnostic failure")
                .contains("postgres.log");
    }

    @Test
    void startupTimeoutAddsConnectionLayoutAndProbeDiagnostics() {
        final PostgresLayout layout = PostgresLayoutFixture.createdLayout(temporaryDirectory.resolve("layout"));
        final PostgresProbeResult probeResult = PostgresProbeResult.unhealthy(
                "pg_isready rejected connection",
                new DiagnosticReport(List.of(new DiagnosticSection("pg_isready", Map.of("stderr", "reject")))));

        final PostgresStartupException result = PostgresStartupDiagnostics.startupTimeout(
                new PostgresConnectionInfo("127.0.0.1", 15432, "postgres", "postgres", Secret.redacted()),
                layout,
                probeResult);

        assertThat(result.diagnosticReport().renderText())
                .contains("15432")
                .contains(layout.dataDirectory().toString())
                .contains("pg_isready rejected connection")
                .contains("reject");
    }

    private static final class DiagnosticManagedPostgresException extends ManagedPostgresException {

        private static final long serialVersionUID = 1L;

        private DiagnosticManagedPostgresException(final DiagnosticReport diagnosticReport) {
            super("diagnostic failure", diagnosticReport);
        }
    }
}
