package eu.virtualparadox.managedpostgres.cli.command.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import eu.virtualparadox.managedpostgres.cli.command.support.CliCommandTestSupport;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgres;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgresFailureFactory;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgresFactory;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.exception.PostgresShutdownException;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class RestartCommandTest {

    RestartCommandTest() {
    }

    @Test
    void restartCommandStopsThenStartsManagedPostgres() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withConnection(connectionInfo())) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runRestart(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(postgres.invocations().stop()).isEqualTo(1);
            assertThat(postgres.invocations().start()).isEqualTo(1);
            assertThat(run.output())
                    .contains("restarted")
                    .contains("host=127.0.0.1")
                    .contains("port=15432")
                    .contains("database=app")
                    .contains("username=app");
        }
    }

    @Test
    void stopOnCloseConfiguresStopOnClosePolicy() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withConnection(connectionInfo())) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runRestart(postgres, "--stop-on-close");

            assertThat(run.configuration()).hasValueSatisfying(configuration ->
                    assertThat(configuration.stopPolicy()).isEqualTo(StopPolicy.STOP_ON_CLOSE));
        }
    }

    @Test
    void keepRunningConfiguresKeepRunningPolicy() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withConnection(connectionInfo())) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runRestart(postgres, "--keep-running");

            assertThat(run.configuration()).hasValueSatisfying(configuration ->
                    assertThat(configuration.stopPolicy()).isEqualTo(StopPolicy.KEEP_RUNNING));
        }
    }

    @Test
    void lifecycleShutdownExceptionMapsToClusterExitCode() {
        final DiagnosticReport diagnostics = new DiagnosticReport(List.of(new DiagnosticSection(
                "shutdown",
                Map.of("password", "secret-password"))));
        final PostgresShutdownException failure = new PostgresShutdownException(
                "shutdown failed password=secret-password",
                diagnostics);

        try (TestManagedPostgres postgres = TestManagedPostgresFailureFactory.withStopFailure(failure)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runRestart(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.CLUSTER_ERROR.code());
            assertThat(run.errorOutput())
                    .contains("Managed Postgres error")
                    .doesNotContain("secret-password");
        }
    }

    private static PostgresConnectionInfo connectionInfo() {
        return new PostgresConnectionInfo(
                "127.0.0.1",
                15432,
                "app",
                "app",
                Secret.of("secret-password"));
    }
}
