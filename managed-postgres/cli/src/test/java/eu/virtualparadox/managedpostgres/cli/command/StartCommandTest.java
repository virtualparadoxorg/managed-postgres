package eu.virtualparadox.managedpostgres.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresConnectionInfo;
import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import eu.virtualparadox.managedpostgres.cli.command.support.CliCommandTestSupport;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgresFailureFactory;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgres;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgresFactory;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.exception.PostgresStartupException;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class StartCommandTest {

    StartCommandTest() {
    }

    @Test
    void startCommandStartsViaManagedPostgresStart() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withConnection(connectionInfo())) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runStart(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(postgres.invocations().start()).isEqualTo(1);
        }
    }

    @Test
    void keepRunningConfiguresKeepRunningStopPolicy() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withConnection(connectionInfo())) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runStart(postgres, "--keep-running");

            assertThat(run.configuration()).hasValueSatisfying(configuration ->
                    assertThat(configuration.stopPolicy()).isEqualTo(StopPolicy.KEEP_RUNNING));
        }
    }

    @Test
    void connectionOutputIncludesNonSecretConnectionFields() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withConnection(connectionInfo())) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runStart(postgres);

            assertThat(run.output())
                    .contains("host=127.0.0.1")
                    .contains("port=15432")
                    .contains("database=app")
                    .contains("username=app");
        }
    }

    @Test
    void connectionOutputNeverIncludesPassword() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withConnection(connectionInfo())) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runStart(postgres);

            assertThat(run.output()).doesNotContain("secret-password");
            assertThat(run.errorOutput()).doesNotContain("secret-password");
        }
    }

    @Test
    void lifecycleStartupExceptionMapsToStartupExitCode() {
        final DiagnosticReport diagnostics = new DiagnosticReport(List.of(new DiagnosticSection(
                "startup",
                Map.of("password", "secret-password"))));
        final PostgresStartupException failure = new PostgresStartupException(
                "startup failed password=secret-password",
                diagnostics);

        try (TestManagedPostgres postgres = TestManagedPostgresFailureFactory.withStartFailure(failure)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runStart(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.STARTUP_ERROR.code());
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
