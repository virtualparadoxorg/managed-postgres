package eu.virtualparadox.managedpostgres.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.cli.CliExitCode;
import eu.virtualparadox.managedpostgres.cli.command.support.CliCommandTestSupport;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgres;
import eu.virtualparadox.managedpostgres.cli.command.support.TestManagedPostgresFactory;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

final class DoctorCommandTest {

    DoctorCommandTest() {
    }

    @Test
    void doctorCommandPrintsRedactedDoctorReportText() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withReport(reportWithSecret())) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runDoctor(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(run.output())
                    .contains("credentials")
                    .contains("password=<redacted>")
                    .doesNotContain("secret-password");
            assertThat(run.errorOutput()).isEmpty();
        }
    }

    @Test
    void doctorJsonFormatPrintsRedactedParseableJson() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withReport(reportWithSecret())) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runDoctor(postgres, "--format", "json");
            final Object document = new Load(LoadSettings.builder().build()).loadFromString(run.output());

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(document)
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .containsEntry("status", "RUNNING");
            assertThat(run.output())
                    .contains("\"password\": \"<redacted>\"")
                    .doesNotContain("secret-password");
        }
    }

    @Test
    void doctorCommandReturnsSuccessForValidReport() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withReport(reportWithSecret())) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runDoctor(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
        }
    }

    @Test
    void doctorCommandDoesNotStartPostgres() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withReport(reportWithSecret())) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runDoctor(postgres);

            assertThat(run.exitCode()).isEqualTo(CliExitCode.OK.code());
            assertThat(postgres.invocations().start()).isZero();
            assertThat(postgres.invocations().close()).isZero();
        }
    }

    @Test
    void doctorCommandOutputContainsNoPassword() {
        try (TestManagedPostgres postgres = TestManagedPostgresFactory.withReport(reportWithSecret())) {
            final CliCommandTestSupport.CliRun run = CliCommandTestSupport.runDoctor(postgres, "--format", "json");

            assertThat(run.output()).doesNotContain("secret-password");
            assertThat(run.errorOutput()).doesNotContain("secret-password");
        }
    }

    private static DoctorReport reportWithSecret() {
        return new DoctorReport(
                PostgresStatus.RUNNING,
                List.of(new DiagnosticSection(
                        "credentials",
                        Map.of("username", "app", "password", "secret-password"))));
    }
}
