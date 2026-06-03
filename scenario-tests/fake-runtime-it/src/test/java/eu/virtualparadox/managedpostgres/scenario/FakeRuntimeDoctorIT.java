package eu.virtualparadox.managedpostgres.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.ManagedPostgres;
import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.scenario.support.LoopbackTcpServer;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioJdbcDriver;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioManagedPostgres;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioMetadata;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioShell;
import eu.virtualparadox.managedpostgres.test.FakePostgresRuntime;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("driver-manager")
final class FakeRuntimeDoctorIT {

    private static final String APP_PASSWORD = "app-password";
    private static final String TEST_PASSWORD = "test-password";

    @TempDir
    private Path temporaryDirectory;

    FakeRuntimeDoctorIT() {}

    @Test
    void doctorReportsRunningFakeRuntimeWithoutLeakingSecrets() throws IOException, SQLException {
        final FakePostgresRuntime runtime = FakePostgresRuntime.create(
                temporaryDirectory.resolve("runtime"),
                ScenarioShell.recordingPgCtl(temporaryDirectory.resolve("pg_ctl-calls.log")));
        final Path storageRoot = temporaryDirectory.resolve("cluster");

        try (ManagedPostgres postgres = ScenarioManagedPostgres.applicationCluster(storageRoot, runtime)
                        .build();
                RunningPostgres running = postgres.start()) {
            assertThat(running.status()).isEqualTo(PostgresStatus.RUNNING);
            final PostgresInstanceMetadata metadata = ScenarioMetadata.require(storageRoot);

            try (LoopbackTcpServer server = LoopbackTcpServer.open(metadata.host(), metadata.port());
                    ScenarioJdbcDriver driver = ScenarioJdbcDriver.register(metadata)) {
                final DoctorReport report = postgres.doctor();

                server.assertHealthy();
                assertThat(report.status()).as(report.renderText()).isEqualTo(PostgresStatus.RUNNING);
                assertThat(section(report, "runtime"))
                        .containsEntry("source", "existing")
                        .containsEntry("status", "usable");
                assertThat(section(report, "credentials"))
                        .containsEntry("status", "present")
                        .containsEntry("readable", "true");
                assertThat(section(report, "metadata"))
                        .containsEntry("status", "present")
                        .containsEntry("clusterId", metadata.clusterId());
                assertThat(section(report, "probes"))
                        .containsEntry("status", "healthy")
                        .containsEntry("compatibility", "compatible")
                        .containsEntry("port", "open")
                        .containsEntry("jdbc", "healthy");
                assertThat(section(report, "status")).containsEntry("value", "RUNNING");
                assertThat(report.renderText()).doesNotContain(TEST_PASSWORD, APP_PASSWORD);
                assertThat(report.renderJson()).doesNotContain(TEST_PASSWORD, APP_PASSWORD);
                assertThat(driver.connections()).isPositive();
            }
        }
    }

    private static Map<String, String> section(final DoctorReport report, final String name) {
        return report.sections().stream()
                .filter(section -> name.equals(section.name()))
                .findFirst()
                .map(DiagnosticSection::values)
                .orElseThrow();
    }
}
