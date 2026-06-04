package eu.virtualparadox.managedpostgres.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.exception.PostgresUpgradeException;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioManagedPostgres;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioMetadata;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioShell;
import eu.virtualparadox.managedpostgres.test.FakePostgresRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FakeRuntimePreflightIT {

    @TempDir
    private Path temporaryDirectory;

    FakeRuntimePreflightIT() {}

    @Test
    void persistentMajorUpgradeFailsBeforeStartAndPreservesMetadata() throws IOException {
        final Path callLog = temporaryDirectory.resolve("pg_ctl-calls.log");
        final FakePostgresRuntime runtime = FakePostgresRuntime.create(
                temporaryDirectory.resolve("runtime"), ScenarioShell.recordingPgCtl(callLog));
        final Path storageRoot = temporaryDirectory.resolve("cluster");

        try (RunningPostgres ignored = ScenarioManagedPostgres.applicationCluster(storageRoot, runtime)
                .stopPolicy(StopPolicy.KEEP_RUNNING)
                .start()) {
            assertThat(ignored.status()).isEqualTo(PostgresStatus.RUNNING);
            assertThat(ScenarioMetadata.metadataPath(storageRoot)).isRegularFile();
        }

        assertThatThrownBy(() -> ScenarioManagedPostgres.applicationCluster(storageRoot, runtime)
                        .version("17.0")
                        .start())
                .isInstanceOf(PostgresUpgradeException.class)
                .satisfies(throwable -> assertThat(((PostgresUpgradeException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("dataDirectoryPostgresqlVersion")
                        .contains("requestedPostgresqlVersion")
                        .contains("17.0")
                        .contains("16"));

        assertThat(ScenarioMetadata.metadataPath(storageRoot)).isRegularFile();
        assertThat(ScenarioMetadata.require(storageRoot).postgresqlVersion()).isEqualTo("16.4");
        assertThat(Files.readAllLines(callLog)).containsExactly("start");
    }

    @Test
    void persistentBootstrapIdentityDriftFailsBeforeStartAndPreservesMetadata() throws IOException {
        final Path pgCtlLog = temporaryDirectory.resolve("pg_ctl-calls.log");
        final Path bootstrapLog = temporaryDirectory.resolve("bootstrap-psql-calls.log");
        final FakePostgresRuntime runtime = FakePostgresRuntime.create(
                temporaryDirectory.resolve("runtime"),
                ScenarioShell.recordingPgCtl(pgCtlLog),
                ScenarioShell.recordingBootstrapPsql(bootstrapLog));
        final Path storageRoot = temporaryDirectory.resolve("cluster");

        try (RunningPostgres ignored = ScenarioManagedPostgres.applicationCluster(storageRoot, runtime)
                .stopPolicy(StopPolicy.KEEP_RUNNING)
                .start()) {
            assertThat(ignored.status()).isEqualTo(PostgresStatus.RUNNING);
            assertThat(ScenarioMetadata.metadataPath(storageRoot)).isRegularFile();
        }
        final PostgresInstanceMetadata originalMetadata = ScenarioMetadata.require(storageRoot);
        final var originalBootstrapCalls = Files.readAllLines(bootstrapLog);

        assertThatThrownBy(() -> ScenarioManagedPostgres.applicationCluster(storageRoot, runtime)
                        .cluster()
                        .database("app_v2")
                        .owner("app_owner_v2")
                        .password("new-app-password")
                        .start())
                .isInstanceOf(PostgresUpgradeException.class)
                .satisfies(throwable -> assertThat(((PostgresUpgradeException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("database")
                        .contains("expected <app_v2>")
                        .contains("was <app>")
                        .contains("owner")
                        .contains("expected <app_owner_v2>")
                        .contains("was <app_owner>")
                        .contains("configHash")
                        .doesNotContain("app-password")
                        .doesNotContain("new-app-password"));

        assertThat(ScenarioMetadata.metadataPath(storageRoot)).isRegularFile();
        assertThat(ScenarioMetadata.staleMetadataPath(storageRoot)).doesNotExist();
        assertThat(ScenarioMetadata.require(storageRoot)).isEqualTo(originalMetadata);
        assertThat(Files.readAllLines(pgCtlLog)).containsExactly("start");
        assertThat(Files.readAllLines(bootstrapLog)).isEqualTo(originalBootstrapCalls);
        assertThat(originalBootstrapCalls)
                .noneSatisfy(call -> assertThat(call).contains("app_v2").contains("app_owner_v2"));
    }
}
