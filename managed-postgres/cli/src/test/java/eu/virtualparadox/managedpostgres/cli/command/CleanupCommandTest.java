package eu.virtualparadox.managedpostgres.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import eu.virtualparadox.managedpostgres.cli.command.support.CliCommandTestSupport;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgres;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgresFactory;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgresFailureFactory;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.exception.PostgresCleanupException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CleanupCommandTest {

    CleanupCommandTest() {}

    @Test
    void cleanupCommandCallsManagedPostgresCleanup() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withStatus(PostgresStatus.STOPPED)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runCleanup(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(postgres.invocations().cleanup()).isEqualTo(1);
        }
    }

    @Test
    void successfulCleanupReturnsSuccess() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withStatus(PostgresStatus.STOPPED)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runCleanup(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(run.output()).contains("cleanup-complete");
        }
    }

    @Test
    void cleanupExceptionMapsToClusterError() {
        final DiagnosticReport diagnostics = new DiagnosticReport(
                List.of(new DiagnosticSection("cleanup-root", Map.of("password", "secret-password"))));
        final PostgresCleanupException failure =
                new PostgresCleanupException("cleanup failed password=secret-password", diagnostics);

        try (TestManagedPostgres postgres = TestManagedPostgresFailureFactory.withCleanupFailure(failure)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runCleanup(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.CLUSTER_ERROR.code());
            assertThat(run.errorOutput()).contains("Managed Postgres error").doesNotContain("secret-password");
        }
    }
}
