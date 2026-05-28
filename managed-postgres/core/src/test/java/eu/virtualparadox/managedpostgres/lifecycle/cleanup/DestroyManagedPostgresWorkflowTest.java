package eu.virtualparadox.managedpostgres.lifecycle.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.exception.PostgresDestroyException;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLockService;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresStartArtifacts;
import eu.virtualparadox.managedpostgres.lifecycle.start.StartPostgresWorkflow;
import eu.virtualparadox.managedpostgres.lifecycle.stop.PostgresStopCommand;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.FakePostgresRuntime;
import eu.virtualparadox.managedpostgres.lifecycle.testsupport.layout.PostgresMetadataFixture;
import eu.virtualparadox.managedpostgres.metadata.ConfigHashCalculator;
import eu.virtualparadox.managedpostgres.metadata.MetadataStore;
import eu.virtualparadox.managedpostgres.metadata.PostgresInstanceMetadata;
import eu.virtualparadox.managedpostgres.runtime.RuntimeResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class DestroyManagedPostgresWorkflowTest {

    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(5);

    @TempDir
    private Path temporaryDirectory;

    DestroyManagedPostgresWorkflowTest() {
    }

    @Test
    void destroyStopsCompatiblePersistentClusterBeforeDeletingLayout() throws IOException {
        final FakePostgresRuntime fakeRuntime = new FakePostgresRuntime(temporaryDirectory);
        final Path runtimeDirectory = fakeRuntime.runtimeWithScripts(java.util.List.of());
        final ManagedPostgresConfiguration configuration =
                CleanupWorkflowTestConfigurations.persistentConfiguration(temporaryDirectory.resolve("cluster"))
                        .withRuntimeSource(RuntimeSource.existing(runtimeDirectory));
        final PostgresLayout layout = PostgresLayout.create(configuration.storage(), new FileSystemOperationJournal());
        writeMetadata(layout, configuration);

        workflow(new FixedRuntimeResolver(runtimeDirectory)).destroyCluster(configuration);

        assertThat(fakeRuntime.calls()).containsExactly("pg_ctl stop");
        assertThat(layout.root()).doesNotExist();
    }

    @Test
    void destroyDeletesStoppedManagedLayoutWithoutMetadataWhenStructureMatches() {
        final ManagedPostgresConfiguration configuration =
                CleanupWorkflowTestConfigurations.persistentConfiguration(temporaryDirectory.resolve("stopped-cluster"))
                        .withRuntimeSource(RuntimeSource.existing(temporaryDirectory.resolve("runtime")));
        final PostgresLayout layout = PostgresLayout.create(configuration.storage(), new FileSystemOperationJournal());

        workflow(new FixedRuntimeResolver(temporaryDirectory.resolve("runtime"))).destroyCluster(configuration);

        assertThat(layout.root()).doesNotExist();
    }

    @Test
    void destroyRejectsTemporaryStorage() {
        final ManagedPostgresConfiguration configuration =
                CleanupWorkflowTestConfigurations.temporaryConfiguration(temporaryDirectory);

        assertThatExceptionOfType(PostgresDestroyException.class)
                .isThrownBy(() -> workflow(new FixedRuntimeResolver(temporaryDirectory.resolve("runtime")))
                        .destroyCluster(configuration))
                .withMessage("Explicit destroy is unsupported for temporary cluster storage");
    }

    @Test
    void destroyRejectsPidFileWithoutMetadata() throws IOException {
        final ManagedPostgresConfiguration configuration =
                CleanupWorkflowTestConfigurations.persistentConfiguration(temporaryDirectory.resolve("cluster-with-pid"))
                        .withRuntimeSource(RuntimeSource.existing(temporaryDirectory.resolve("runtime")));
        final PostgresLayout layout = PostgresLayout.create(configuration.storage(), new FileSystemOperationJournal());
        Files.writeString(layout.dataDirectory().resolve("postmaster.pid"), "12345\n");

        assertThatExceptionOfType(PostgresDestroyException.class)
                .isThrownBy(() -> workflow(new FixedRuntimeResolver(temporaryDirectory.resolve("runtime")))
                        .destroyCluster(configuration))
                .withMessage("Managed PostgreSQL destroy refused because live PostgreSQL ownership could not be verified");
        assertThat(layout.root()).exists();
    }

    @Test
    void destroyRejectsUnmanagedPersistentStoragePath() throws IOException {
        final Path unmanagedRoot = temporaryDirectory.resolve("unmanaged-root");
        Files.createDirectories(unmanagedRoot);
        Files.writeString(unmanagedRoot.resolve("notes.txt"), "unmanaged");
        final ManagedPostgresConfiguration configuration =
                CleanupWorkflowTestConfigurations.persistentConfiguration(unmanagedRoot);

        assertThatExceptionOfType(PostgresDestroyException.class)
                .isThrownBy(() -> workflow(new FixedRuntimeResolver(temporaryDirectory.resolve("runtime")))
                        .destroyCluster(configuration))
                .withMessage("Managed PostgreSQL destroy refused because storage does not look framework-managed");
        assertThat(unmanagedRoot).exists();
    }

    @Test
    void destroyMissingPersistentLayoutIsANoop() {
        final ManagedPostgresConfiguration configuration =
                CleanupWorkflowTestConfigurations.persistentConfiguration(temporaryDirectory.resolve("missing-cluster"))
                        .withRuntimeSource(RuntimeSource.existing(temporaryDirectory.resolve("runtime")));

        workflow(new FixedRuntimeResolver(temporaryDirectory.resolve("runtime"))).destroyCluster(configuration);

        assertThat(configuration.storage().path()).doesNotExist();
    }

    @Test
    void destroyRejectsMetadataMismatchBeforeDeletingLayout() throws IOException {
        final ManagedPostgresConfiguration configuration =
                CleanupWorkflowTestConfigurations.persistentConfiguration(temporaryDirectory.resolve("cluster-mismatch"))
                        .withRuntimeSource(RuntimeSource.existing(temporaryDirectory.resolve("runtime")));
        final PostgresLayout layout = PostgresLayout.create(configuration.storage(), new FileSystemOperationJournal());
        writeMetadata(layout, configuration.withName("other-db"));

        assertThatExceptionOfType(PostgresDestroyException.class)
                .isThrownBy(() -> workflow(new FixedRuntimeResolver(temporaryDirectory.resolve("runtime")))
                        .destroyCluster(configuration))
                .withMessage("Managed PostgreSQL destroy failed");
        assertThat(layout.root()).exists();
    }

    private DestroyManagedPostgresWorkflow workflow(final RuntimeResolver runtimeResolver) {
        return new DestroyManagedPostgresWorkflow(
                new PostgresStopCommand(runtimeResolver, STOP_TIMEOUT),
                new FileSystemOperationJournal(),
                new PostgresLockService());
    }

    private static void writeMetadata(
            final PostgresLayout layout,
            final ManagedPostgresConfiguration configuration) {
        new MetadataStore(layout.metadataPath(), new FileSystemOperationJournal())
                .write(metadata(layout, configuration));
    }

    private static PostgresInstanceMetadata metadata(
            final PostgresLayout layout,
            final ManagedPostgresConfiguration configuration) {
        final PostgresInstanceMetadata base = PostgresMetadataFixture.metadata(layout.dataDirectory(), 15432);

        return new PostgresInstanceMetadata(
                base.schemaVersion(),
                base.instanceId(),
                base.clusterId(),
                configuration.name(),
                base.dataDirectory(),
                base.host(),
                base.port(),
                base.database(),
                base.owner(),
                base.postgresqlVersion(),
                base.postgresqlMajor(),
                base.attachmentMode(),
                base.pid(),
                new ConfigHashCalculator().calculate(PostgresStartArtifacts.configHashSettings(
                        new StartPostgresWorkflow.Configuration(configuration),
                        base.host(),
                        base.port())),
                base.createdAt(),
                base.updatedAt());
    }

    private record FixedRuntimeResolver(Path runtimeDirectory) implements RuntimeResolver {

        private FixedRuntimeResolver {
            java.util.Objects.requireNonNull(runtimeDirectory, "runtimeDirectory");
        }

        @Override
        public Path resolve(final RuntimeSource runtimeSource) {
            return runtimeDirectory;
        }

        @Override
        public Path resolve(
                final RuntimeSource runtimeSource,
                final String postgresqlVersion) {
            return runtimeDirectory;
        }
    }
}
