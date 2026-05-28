package eu.virtualparadox.managedpostgres.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import eu.virtualparadox.managedpostgres.cli.command.support.CliCommandTestSupport;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgres;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgresFactory;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

final class StatusCommandTest {

    StatusCommandTest() {
    }

    @Test
    void statusCommandPrintsStoppedWhenLifecycleReportsMissingMetadata() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withStatus(PostgresStatus.STOPPED)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runStatus(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(run.output()).contains("STOPPED");
            assertThat(run.errorOutput()).isEmpty();
        }
    }

    @Test
    void statusCommandReturnsSuccessWhenLifecycleReturnsAStatus() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withStatus(PostgresStatus.RUNNING)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runStatus(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(run.output()).contains("RUNNING");
        }
    }

    @Test
    void statusJsonFormatPrintsParseableJson() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withStatus(PostgresStatus.STOPPED)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runStatus(postgres, "--format", "json");
            final Object document = new Load(LoadSettings.builder().build()).loadFromString(run.output());

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(document)
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .containsEntry("status", "STOPPED");
        }
    }

    @Test
    void statusCommandDoesNotStartPostgres() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withStatus(PostgresStatus.RUNNING)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runStatus(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(postgres.invocations().start()).isZero();
            assertThat(postgres.invocations().close()).isZero();
        }
    }

    @Test
    void statusCommandOutputContainsNoPassword() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withStatus(PostgresStatus.RUNNING)) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runStatus(postgres, "--format", "json");

            assertThat(run.output()).doesNotContain("secret-password");
            assertThat(run.errorOutput()).doesNotContain("secret-password");
        }
    }
}
