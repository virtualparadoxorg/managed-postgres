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
import eu.virtualparadox.managedpostgres.exception.PostgresDestroyException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DestroyCommandTest {

    DestroyCommandTest() {}

    @Test
    void destroyCommandRequiresForceFlag() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withStatus(PostgresStatus.STOPPED)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runDestroy(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.CONFIGURATION_ERROR.code());
            assertThat(run.errorOutput()).contains("--force is required for destroy");
            assertThat(postgres.invocations().destroy()).isZero();
        }
    }

    @Test
    void destroyCommandCallsManagedPostgresDestroyWhenForced() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withStatus(PostgresStatus.STOPPED)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runDestroy(postgres, "--force");

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(postgres.invocations().destroy()).isEqualTo(1);
            assertThat(run.output()).contains("destroy-complete");
        }
    }

    @Test
    void destroyExceptionMapsToClusterError() {
        final DiagnosticReport diagnostics = new DiagnosticReport(
                List.of(new DiagnosticSection("storage-root", Map.of("password", "secret-password"))));
        final PostgresDestroyException failure =
                new PostgresDestroyException("destroy failed password=secret-password", diagnostics);

        try (TestManagedPostgres postgres = TestManagedPostgresFailureFactory.withDestroyFailure(failure)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runDestroy(postgres, "--force");

            assertThat(run.exitCode()).isEqualTo(CliExitCode.CLUSTER_ERROR.code());
            assertThat(run.errorOutput()).contains("Managed Postgres error").doesNotContain("secret-password");
        }
    }
}
