package eu.virtualparadox.managedpostgres.lifecycle.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.config.AttachPolicy;
import eu.virtualparadox.managedpostgres.config.ClusterBootstrap;
import eu.virtualparadox.managedpostgres.config.Credentials;
import eu.virtualparadox.managedpostgres.config.model.ConfigDriftPolicy;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.StopPolicy;
import eu.virtualparadox.managedpostgres.config.Storage;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.model.UpgradePolicy;
import eu.virtualparadox.managedpostgres.config.network.Network;
import eu.virtualparadox.managedpostgres.config.postgresql.Resources;
import eu.virtualparadox.managedpostgres.diagnostics.DiagnosticSection;
import eu.virtualparadox.managedpostgres.diagnostics.DoctorReport;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.metadata.ConfigHashCalculator;
import eu.virtualparadox.managedpostgres.metadata.MetadataStore;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.security.Secret;
import java.util.Optional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import eu.virtualparadox.managedpostgres.lifecycle.attach.AttachValidation;
import eu.virtualparadox.managedpostgres.lifecycle.probe.PostgresProbeResult;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresStartArtifacts;
import eu.virtualparadox.managedpostgres.lifecycle.process.ProcessLookup;
import eu.virtualparadox.managedpostgres.lifecycle.ManagedPostgresService;

public final class DoctorServiceTest {

    @TempDir
    private Path temporaryDirectory;

    DoctorServiceTest() {
    }

    @Test
    void doctorDoesNotCreateDirectoriesForNeverStartedPersistentConfiguration() {
        final Path root = temporaryDirectory.resolve("cluster");
        final DoctorReport report = doctor(configuration(root));

        assertThat(Files.exists(root)).isFalse();
        assertThat(report.status()).isEqualTo(PostgresStatus.STOPPED);
    }

    @Test
    void doctorDoesNotCreateDirectoriesForTemporaryConfiguration() {
        final Path root = temporaryDirectory.resolve("temporary-parent");
        final DoctorReport report = doctor(configuration(root).withStorage(new Storage(root, true)));

        assertThat(Files.exists(root)).isFalse();
        assertThat(report.status()).isEqualTo(PostgresStatus.STOPPED);
        assertThat(section(report, "configuration")).containsEntry("storageKind", "temporary");
        assertThat(section(report, "layout")).containsEntry("status", "not-created-temporary");
        assertThat(section(report, "credentials")).containsEntry("status", "not-created-temporary");
    }

    @Test
    void doctorReportsStoppedWhenMetadataIsAbsent() {
        final Path root = temporaryDirectory.resolve("cluster");
        final DoctorReport report = doctor(configuration(root));

        assertThat(report.status()).isEqualTo(PostgresStatus.STOPPED);
        assertThat(section(report, "metadata")).containsEntry("status", "absent");
        assertThat(section(report, "status")).containsEntry("value", "STOPPED");
    }

    @Test
    void doctorIncludesConfigurationRuntimeAndLayoutDiagnostics() {
        final Path root = temporaryDirectory.resolve("cluster");
        final DoctorReport report = doctor(configuration(root).withPostgresConfiguration(Resources.small()));

        assertThat(section(report, "configuration"))
                .containsEntry("name", "app-db")
                .containsEntry("postgresqlVersion", "16.4")
                .containsEntry("storageKind", "persistent")
                .containsEntry("runtimeSource", "system")
                .containsEntry("networkHost", "127.0.0.1")
                .containsEntry("portSelection", "RANDOM")
                .containsEntry("fallbackToRandom", "false")
                .containsEntry("attachPolicy", AttachPolicy.ATTACH_IF_COMPATIBLE.name())
                .containsEntry("stopPolicy", StopPolicy.KEEP_RUNNING.name())
                .containsEntry("maxConnections", "32")
                .containsEntry("sharedBuffers", "128MB")
                .containsEntry("tempBuffers", "16MB")
                .containsEntry("statementTimeoutSeconds", "30");
        assertThat(section(report, "runtime"))
                .containsEntry("source", "system")
                .containsEntry("status", "not-inspected");
        assertThat(section(report, "credentials"))
                .containsEntry("status", "absent")
                .containsEntry("path", root.resolve("state/credentials.properties")
                        .toAbsolutePath().normalize().toString());
        assertThat(section(report, "layout"))
                .containsEntry("root", root.toAbsolutePath().normalize().toString())
                .containsEntry("dataDirectory", root.resolve("data").toAbsolutePath().normalize().toString())
                .containsEntry("runtimeDirectory", root.resolve("runtime").toAbsolutePath().normalize().toString())
                .containsEntry("stateDirectory", root.resolve("state").toAbsolutePath().normalize().toString())
                .containsEntry("metadataPath", root.resolve("state/metadata.json").toAbsolutePath().normalize().toString());
    }

    @Test
    void doctorReportsReadableMetadataFields() {
        final Path root = temporaryDirectory.resolve("cluster");
        writeMetadata(root, metadata(root));

        final DoctorReport report = doctor(configuration(root));

        assertThat(section(report, "metadata"))
                .containsEntry("status", "present")
                .containsEntry("clusterId", "cluster-1")
                .containsEntry("database", "app")
                .containsEntry("port", "15432");
    }

    @Test
    void doctorReportsRunningWhenMetadataAndHealthProbesAreHealthy() {
        final Path root = temporaryDirectory.resolve("cluster");
        writeMetadata(root, compatibleMetadata(root, 0L));

        final DoctorReport report = doctor(
                configuration(root),
                new DoctorProbeInspector(new AttachValidation(
                        ProcessLookup.fixed(Optional.empty()),
                        metadata -> true,
                        request -> PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"))));

        assertThat(report.status()).isEqualTo(PostgresStatus.RUNNING);
        assertThat(section(report, "probes"))
                .containsEntry("status", "healthy")
                .containsEntry("port", "open")
                .containsEntry("jdbc", "healthy");
        assertThat(section(report, "status")).containsEntry("value", "RUNNING");
    }

    @Test
    void doctorReportsFailedWhenMetadataExistsButHealthProbeFails() {
        final Path root = temporaryDirectory.resolve("cluster");
        writeMetadata(root, compatibleMetadata(root, 0L));

        final DoctorReport report = doctor(
                configuration(root),
                new DoctorProbeInspector(new AttachValidation(
                        ProcessLookup.fixed(Optional.empty()),
                        metadata -> false,
                        request -> PostgresProbeResult.healthy("JDBC probe confirms PostgreSQL identity"))));

        assertThat(report.status()).isEqualTo(PostgresStatus.FAILED);
        assertThat(section(report, "probes"))
                .containsEntry("status", "unhealthy")
                .containsEntry("port", "closed")
                .containsEntry("jdbc", "skipped");
        assertThat(section(report, "status")).containsEntry("value", "FAILED");
    }

    @Test
    void doctorReportsFailedWhenMetadataCannotBeParsed() throws IOException {
        final Path root = temporaryDirectory.resolve("cluster");
        final Path stateDirectory = root.resolve("state");
        final Path metadataPath = stateDirectory.resolve("metadata.json");
        Files.createDirectories(stateDirectory);
        Files.writeString(metadataPath, "{\"schemaVersion\":1}");

        final DoctorReport report = doctor(configuration(root));

        assertThat(report.status()).isEqualTo(PostgresStatus.FAILED);
        assertThat(section(report, "metadata")).containsEntry("status", "unreadable");
        assertThat(report.renderText()).contains("postgres-metadata").contains("metadata.json");
    }

    @Test
    void doctorDoesNotRenderConfiguredPasswords() {
        final String secret = "never-render-this-secret";
        final ManagedPostgresConfiguration configuration = configuration(temporaryDirectory.resolve("cluster"))
                .withCredentials(Credentials.of("app", Secret.of(secret)))
                .withClusterBootstrap(ClusterBootstrap.defaultCluster()
                        .database("app")
                        .owner("app")
                        .password(Secret.of(secret)));

        final DoctorReport report = doctor(configuration);

        assertThat(report.renderText()).doesNotContain(secret);
        assertThat(report.renderJson()).doesNotContain(secret);
    }

    @Test
    void managedPostgresServiceExposesDoctorDiagnostics() {
        final DoctorReport report = new ManagedPostgresService().doctor(configuration(temporaryDirectory.resolve("cluster")));

        assertThat(report.status()).isEqualTo(PostgresStatus.STOPPED);
        assertThat(section(report, "configuration")).containsEntry("name", "app-db");
    }

    private DoctorReport doctor(final ManagedPostgresConfiguration configuration) {
        return new DoctorService(new FileSystemOperationJournal()).doctor(configuration);
    }

    private DoctorReport doctor(
            final ManagedPostgresConfiguration configuration,
            final DoctorProbeInspector probeInspector) {
        final FileSystemOperationJournal fileSystem = new FileSystemOperationJournal();
        return new DoctorService(DoctorDependencies.withProbeInspector(fileSystem, probeInspector))
                .doctor(configuration);
    }

    private void writeMetadata(final Path root, final PostgresInstanceMetadata metadata) {
        final Path metadataPath = root.resolve("state/metadata.json");
        new MetadataStore(metadataPath, new FileSystemOperationJournal()).write(metadata);
    }

    private static ManagedPostgresConfiguration configuration(final Path root) {
        return new ManagedPostgresConfiguration(
                "app-db",
                "16.4",
                Storage.projectLocal(root),
                RuntimeSource.system(),
                Credentials.of("app", Secret.of("dev-password")),
                Network.localhostOnly(),
                ClusterBootstrap.defaultCluster().database("app").owner("app").password(Secret.of("dev-password")),
                AttachPolicy.ATTACH_IF_COMPATIBLE,
                StopPolicy.KEEP_RUNNING,
                UpgradePolicy.MINOR_ONLY,
                ConfigDriftPolicy.FAIL,
                CleanupPolicy.safeDefaults());
    }

    private static PostgresInstanceMetadata metadata(final Path root) {
        final Instant now = Instant.parse("2026-05-27T00:00:00Z");

        return new PostgresInstanceMetadata(
                1,
                "instance-1",
                "cluster-1",
                "app-db",
                root.resolve("data"),
                "127.0.0.1",
                15432,
                "app",
                "app",
                "16.4",
                16,
                "started",
                1234,
                "hash",
                now,
                now);
    }

    private static PostgresInstanceMetadata compatibleMetadata(final Path root, final long pid) {
        final Instant now = Instant.parse("2026-05-27T00:00:00Z");

        return new PostgresInstanceMetadata(
                1,
                "instance-1",
                "cluster-1",
                "app-db",
                root.resolve("data"),
                "127.0.0.1",
                15432,
                "app",
                "app",
                "16.4",
                16,
                "started",
                pid,
                new ConfigHashCalculator().calculate(PostgresStartArtifacts.settings("127.0.0.1", 15432)),
                now,
                now);
    }

    private static Map<String, String> section(final DoctorReport report, final String name) {
        return report.sections().stream()
                .filter(section -> name.equals(section.name()))
                .findFirst()
                .map(DiagnosticSection::values)
                .orElseThrow();
    }
}
