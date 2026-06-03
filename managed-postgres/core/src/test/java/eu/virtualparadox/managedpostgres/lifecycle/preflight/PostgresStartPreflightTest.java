package eu.virtualparadox.managedpostgres.lifecycle.preflight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.exception.PostgresUpgradeException;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.ManagedPostgresConfigurationFixture;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresLayoutFixture;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresMetadataFixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class PostgresStartPreflightTest {

    @TempDir
    private Path temporaryDirectory;

    private final PostgresStartPreflight preflight = new PostgresStartPreflight();

    PostgresStartPreflightTest() {}

    @Test
    void dataDirectoryMajorMismatchFailsBeforeStartMutation() throws IOException {
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final var layout = PostgresLayoutFixture.createdLayout(storageRoot);
        Files.writeString(layout.dataDirectory().resolve("PG_VERSION"), "17%n".formatted(), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> preflight.verifyBeforeStart(
                        configuration(storageRoot, "16.4", UpgradePolicy.MINOR_ONLY, ConfigDriftPolicy.FAIL),
                        layout,
                        Optional.empty()))
                .isInstanceOf(PostgresUpgradeException.class)
                .satisfies(throwable -> assertThat(((PostgresUpgradeException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("PG_VERSION")
                        .contains("requestedPostgresqlVersion")
                        .contains("dataDirectoryPostgresqlVersion"));
    }

    @Test
    void existingMetadataMinorVersionMismatchFailsWhenUpgradesAreDisabled() {
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final var layout = PostgresLayoutFixture.createdLayout(storageRoot);
        final var metadata = PostgresMetadataFixture.compatibleMetadata(layout.dataDirectory());

        assertThatThrownBy(() -> preflight.verifyBeforeStart(
                        configuration(storageRoot, "16.5", UpgradePolicy.DISABLED, ConfigDriftPolicy.FAIL),
                        layout,
                        Optional.of(metadata)))
                .isInstanceOf(PostgresUpgradeException.class)
                .hasMessageContaining("version");
    }

    @Test
    void existingMetadataConfigHashMismatchFailsWhenConfigDriftFails() {
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final var layout = PostgresLayoutFixture.createdLayout(storageRoot);
        final var metadata = PostgresMetadataFixture.metadataWithConfigHash(layout.dataDirectory(), "old-hash");

        assertThatThrownBy(() -> preflight.verifyBeforeStart(
                        configuration(storageRoot, "16.4", UpgradePolicy.MINOR_ONLY, ConfigDriftPolicy.FAIL),
                        layout,
                        Optional.of(metadata)))
                .isInstanceOf(PostgresUpgradeException.class)
                .satisfies(throwable -> assertThat(((PostgresUpgradeException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("configHash"));
    }

    @Test
    void existingMetadataConfigHashMismatchIsAcceptedWhenConfigDriftIsIgnored() {
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final var layout = PostgresLayoutFixture.createdLayout(storageRoot);
        final var metadata = PostgresMetadataFixture.metadataWithConfigHash(layout.dataDirectory(), "old-hash");

        assertThatCode(() -> preflight.verifyBeforeStart(
                        configuration(storageRoot, "16.4", UpgradePolicy.MINOR_ONLY, ConfigDriftPolicy.IGNORE),
                        layout,
                        Optional.of(metadata)))
                .doesNotThrowAnyException();
    }

    @Test
    void existingMetadataDatabaseMismatchFailsBeforeStartMutation() {
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final var layout = PostgresLayoutFixture.createdLayout(storageRoot);
        final var metadata = PostgresMetadataFixture.compatibleMetadata(layout.dataDirectory());

        assertThatThrownBy(() -> preflight.verifyBeforeStart(
                        configuration(
                                storageRoot,
                                "16.4",
                                UpgradePolicy.MINOR_ONLY,
                                ConfigDriftPolicy.FAIL,
                                ClusterBootstrap.defaultCluster().database("app")),
                        layout,
                        Optional.of(metadata)))
                .isInstanceOf(PostgresUpgradeException.class)
                .satisfies(throwable -> assertThat(((PostgresUpgradeException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("database")
                        .contains("expected <app>")
                        .contains("was <postgres>"));
    }

    @Test
    void existingMetadataOwnerMismatchFailsBeforeStartMutation() {
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final var layout = PostgresLayoutFixture.createdLayout(storageRoot);
        final var metadata = PostgresMetadataFixture.compatibleMetadata(layout.dataDirectory());

        assertThatThrownBy(() -> preflight.verifyBeforeStart(
                        configuration(
                                storageRoot,
                                "16.4",
                                UpgradePolicy.MINOR_ONLY,
                                ConfigDriftPolicy.FAIL,
                                ClusterBootstrap.defaultCluster().owner("app_owner")),
                        layout,
                        Optional.of(metadata)))
                .isInstanceOf(PostgresUpgradeException.class)
                .satisfies(throwable -> assertThat(((PostgresUpgradeException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("owner")
                        .contains("expected <app_owner>")
                        .contains("was <postgres>"));
    }

    @Test
    void emptyDataDirectoryWithoutMetadataPasses() {
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final var layout = PostgresLayoutFixture.createdLayout(storageRoot);

        assertThatCode(() -> preflight.verifyBeforeStart(
                        configuration(storageRoot, "16.4", UpgradePolicy.MINOR_ONLY, ConfigDriftPolicy.FAIL),
                        layout,
                        Optional.empty()))
                .doesNotThrowAnyException();
    }

    private static StartPostgresWorkflow.Configuration configuration(
            final Path storageRoot,
            final String postgresqlVersion,
            final UpgradePolicy upgradePolicy,
            final ConfigDriftPolicy configDriftPolicy) {
        return configuration(
                storageRoot, postgresqlVersion, upgradePolicy, configDriftPolicy, ClusterBootstrap.defaultCluster());
    }

    private static StartPostgresWorkflow.Configuration configuration(
            final Path storageRoot,
            final String postgresqlVersion,
            final UpgradePolicy upgradePolicy,
            final ConfigDriftPolicy configDriftPolicy,
            final ClusterBootstrap clusterBootstrap) {
        final var configuration = ManagedPostgresConfigurationFixture.configuration(storageRoot)
                .withPostgresqlVersion(postgresqlVersion)
                .withUpgradePolicy(upgradePolicy)
                .withConfigDriftPolicy(configDriftPolicy)
                .withClusterBootstrap(clusterBootstrap);

        return new StartPostgresWorkflow.Configuration(configuration);
    }
}
