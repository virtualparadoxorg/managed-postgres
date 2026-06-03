package eu.virtualparadox.managedpostgres.lifecycle.attach;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticReport;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.ManagedPostgresConfigurationFixture;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresLayoutFixture;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresMetadataFixture;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class PostgresAttachCompatibilityTest {

    @TempDir
    private Path temporaryDirectory;

    private final PostgresAttachCompatibility compatibility = new PostgresAttachCompatibility();

    PostgresAttachCompatibilityTest() {}

    @Test
    void majorVersionMetadataMismatchRejectsAttach() {
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final var layout = PostgresLayoutFixture.createdLayout(storageRoot);
        final var metadata = PostgresMetadataFixture.metadataWithVersion(layout.dataDirectory(), "17.0", 17);

        assertThat(compatibility.mismatch(
                        configuration(storageRoot, "16.4", UpgradePolicy.MINOR_ONLY, ConfigDriftPolicy.FAIL),
                        layout,
                        metadata))
                .hasValueSatisfying(mismatch -> assertThat(mismatch).contains("major"));
    }

    @Test
    void minorVersionMetadataMismatchAttachesUnderMinorOnlyPolicy() {
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final var layout = PostgresLayoutFixture.createdLayout(storageRoot);
        final var metadata = PostgresMetadataFixture.compatibleMetadata(layout.dataDirectory());

        assertThat(compatibility.mismatch(
                        configuration(storageRoot, "16.5", UpgradePolicy.MINOR_ONLY, ConfigDriftPolicy.FAIL),
                        layout,
                        metadata))
                .isEmpty();
    }

    @Test
    void minorVersionMetadataMismatchFailsWhenUpgradesAreDisabled() {
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final var layout = PostgresLayoutFixture.createdLayout(storageRoot);
        final var metadata = PostgresMetadataFixture.compatibleMetadata(layout.dataDirectory());

        assertThat(compatibility.mismatch(
                        configuration(storageRoot, "16.5", UpgradePolicy.DISABLED, ConfigDriftPolicy.FAIL),
                        layout,
                        metadata))
                .hasValueSatisfying(mismatch -> assertThat(mismatch).contains("version"));
    }

    @Test
    void configHashMismatchFailsAttachUnderFailPolicy() {
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final var layout = PostgresLayoutFixture.createdLayout(storageRoot);
        final var metadata = PostgresMetadataFixture.metadataWithConfigHash(layout.dataDirectory(), "old-hash");

        assertThat(compatibility.mismatch(
                        configuration(storageRoot, "16.4", UpgradePolicy.MINOR_ONLY, ConfigDriftPolicy.FAIL),
                        layout,
                        metadata))
                .hasValueSatisfying(mismatch -> assertThat(mismatch).contains("configHash"));
    }

    @Test
    void configHashMismatchAttachesUnderIgnorePolicy() {
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final var layout = PostgresLayoutFixture.createdLayout(storageRoot);
        final var metadata = PostgresMetadataFixture.metadataWithConfigHash(layout.dataDirectory(), "old-hash");

        assertThat(compatibility.mismatch(
                        configuration(storageRoot, "16.4", UpgradePolicy.MINOR_ONLY, ConfigDriftPolicy.IGNORE),
                        layout,
                        metadata))
                .isEmpty();
    }

    @Test
    void structuredReportIncludesDeterministicMismatchDetails() {
        final Path storageRoot = temporaryDirectory.resolve("local-postgres");
        final var layout = PostgresLayoutFixture.createdLayout(storageRoot);
        final var metadata = PostgresMetadataFixture.compatibleMetadata(layout.dataDirectory());

        final StartPostgresWorkflow.Configuration configuration = applicationConfiguration(storageRoot);
        final DiagnosticReport report = compatibility.diagnosticReport(configuration, layout, metadata);

        assertThat(compatibility.mismatch(configuration, layout, metadata))
                .hasValueSatisfying(summary -> assertThat(summary)
                        .contains("database")
                        .contains("owner")
                        .contains("configHash"));
        assertThat(compatibilityValues(report))
                .containsEntry("status", "incompatible")
                .containsEntry("mismatchCount", "3")
                .containsEntry("mismatch.1.field", "database")
                .containsEntry("mismatch.1.expected", "app")
                .containsEntry("mismatch.1.actual", "postgres")
                .containsEntry("mismatch.2.field", "owner")
                .containsEntry("mismatch.2.expected", "app_owner")
                .containsEntry("mismatch.2.actual", "postgres")
                .containsEntry("mismatch.3.field", "configHash");
    }

    private static StartPostgresWorkflow.Configuration configuration(
            final Path storageRoot,
            final String postgresqlVersion,
            final UpgradePolicy upgradePolicy,
            final ConfigDriftPolicy configDriftPolicy) {
        final var configuration = ManagedPostgresConfigurationFixture.configuration(storageRoot)
                .withPostgresqlVersion(postgresqlVersion)
                .withUpgradePolicy(upgradePolicy)
                .withConfigDriftPolicy(configDriftPolicy);

        return new StartPostgresWorkflow.Configuration(configuration);
    }

    private static StartPostgresWorkflow.Configuration applicationConfiguration(final Path storageRoot) {
        final var configuration = ManagedPostgresConfigurationFixture.configuration(storageRoot)
                .withClusterBootstrap(
                        ClusterBootstrap.defaultCluster().database("app").owner("app_owner"));

        return new StartPostgresWorkflow.Configuration(configuration);
    }

    private static Map<String, String> compatibilityValues(final DiagnosticReport report) {
        return report.sections().stream()
                .filter(section -> "postgres-attach-compatibility".equals(section.name()))
                .findFirst()
                .map(DiagnosticSection::values)
                .orElseThrow();
    }
}
