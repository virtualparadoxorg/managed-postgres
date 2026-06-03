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
import eu.virtualparadox.managedpostgres.exception.PostgresShutdownException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class StopCommandTest {

    StopCommandTest() {}

    @Test
    void stopCommandCallsManagedPostgresStop() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withStatus(PostgresStatus.RUNNING)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runStop(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(postgres.invocations().stop()).isEqualTo(1);
        }
    }

    @Test
    void successfulStopReturnsSuccess() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withStatus(PostgresStatus.RUNNING)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runStop(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(run.output()).contains("stopped");
        }
    }

    @Test
    void shutdownExceptionMapsToClusterError() {
        final DiagnosticReport diagnostics =
                new DiagnosticReport(List.of(new DiagnosticSection("shutdown", Map.of("password", "secret-password"))));
        final PostgresShutdownException failure =
                new PostgresShutdownException("shutdown failed password=secret-password", diagnostics);

        try (TestManagedPostgres postgres = TestManagedPostgresFailureFactory.withStopFailure(failure)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runStop(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.CLUSTER_ERROR.code());
            assertThat(run.errorOutput()).contains("Managed Postgres error").doesNotContain("secret-password");
        }
    }

    @Test
    void stopOutputNeverIncludesPassword() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withStatus(PostgresStatus.RUNNING)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runStop(postgres);

            assertThat(run.output()).doesNotContain("secret-password");
            assertThat(run.errorOutput()).doesNotContain("secret-password");
        }
    }
}
