package eu.virtualparadox.managedpostgres.lifecycle.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.config.cleanup.CleanupPolicy;
import eu.virtualparadox.managedpostgres.config.model.ManagedPostgresConfiguration;
import eu.virtualparadox.managedpostgres.exception.PostgresCleanupException;
import eu.virtualparadox.managedpostgres.filesystem.FileSystemOperationJournal;
import eu.virtualparadox.managedpostgres.filesystem.ManagedPathOwnership;
import eu.virtualparadox.managedpostgres.lifecycle.layout.PostgresLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class CleanupManagedPostgresWorkflowTest {

    @TempDir
    private Path temporaryDirectory;

    CleanupManagedPostgresWorkflowTest() {}

    @Test
    void cleanupRecoversOwnedClusterStagingAndPreservesPersistentData() throws IOException {
        final ManagedPostgresConfiguration configuration =
                CleanupWorkflowTestConfigurations.persistentConfiguration(temporaryDirectory.resolve("cluster"));
        final FileSystemOperationJournal fileSystem = new FileSystemOperationJournal();
        final PostgresLayout layout = PostgresLayout.create(configuration.storage(), fileSystem);
        final ManagedPathOwnership ownership = new ManagedPathOwnership();
        final Path rootStaging = layout.root().resolve("root-copy.staging");
        final Path dataStaging = layout.dataDirectory().resolve("data-copy.staging");
        final Path stateStaging = layout.stateDirectory().resolve("state-copy.staging");
        ownership.writeMarker(rootStaging, "cleanup-root");
        ownership.writeMarker(dataStaging, "cleanup-data");
        ownership.writeMarker(stateStaging, "cleanup-state");
        Files.writeString(layout.dataDirectory().resolve("keep.txt"), "keep");
        Files.writeString(layout.stateDirectory().resolve("postgres.log"), "active");

        new CleanupManagedPostgresWorkflow().cleanup(configuration);

        assertThat(rootStaging).doesNotExist();
        assertThat(dataStaging).doesNotExist();
        assertThat(stateStaging).doesNotExist();
        assertThat(layout.dataDirectory().resolve("keep.txt")).hasContent("keep");
        assertThat(layout.stateDirectory().resolve("postgres.log")).hasContent("active");
        assertThat(layout.root()).exists();
    }

    @Test
    void cleanupTrimsRotatedLogHistoryWithoutTouchingActiveLog() throws IOException {
        final CleanupPolicy cleanupPolicy = CleanupPolicy.safeDefaults().keepLogFiles(2);
        final ManagedPostgresConfiguration configuration = persistentConfiguration(
                temporaryDirectory.resolve("cluster-logs"), cleanupPolicy, RuntimeSource.system());
        final PostgresLayout layout = PostgresLayout.create(configuration.storage(), new FileSystemOperationJournal());
        final Path activeLog = layout.stateDirectory().resolve("postgres.log");
        Files.writeString(activeLog, "active");
        Files.writeString(layout.stateDirectory().resolve("postgres.log.1"), "1");
        Files.writeString(layout.stateDirectory().resolve("postgres.log.2"), "2");
        Files.writeString(layout.stateDirectory().resolve("postgres.log.3"), "3");

        new CleanupManagedPostgresWorkflow().cleanup(configuration);

        assertThat(activeLog).hasContent("active");
        assertThat(layout.stateDirectory().resolve("postgres.log.1")).exists();
        assertThat(layout.stateDirectory().resolve("postgres.log.2")).exists();
        assertThat(layout.stateDirectory().resolve("postgres.log.3")).doesNotExist();
    }

    @Test
    void cleanupCleansRuntimeCachePartialsAndOwnedStaging() throws IOException {
        final Path cacheRoot = temporaryDirectory.resolve("runtime-cache");
        final ManagedPostgresConfiguration configuration = persistentConfiguration(
                temporaryDirectory.resolve("cluster-runtime"),
                CleanupPolicy.safeDefaults(),
                RuntimeSource.downloaded(downloadedRuntime -> downloadedRuntime
                        .cache(RuntimeCache.projectLocal(cacheRoot))
                        .checksum("sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")));
        final ManagedPathOwnership ownership = new ManagedPathOwnership();
        final Path download = cacheRoot.resolve("downloads/postgres.download");
        final Path staging = cacheRoot.resolve("runtimes/postgres.staging");
        final Path finalRuntime = cacheRoot.resolve("runtimes/postgres-final");
        Files.createDirectories(Objects.requireNonNull(download.getParent(), "downloadParent"));
        Files.writeString(download, "partial");
        ownership.writeMarker(staging, "download-runtime");
        Files.createDirectories(finalRuntime);
        Files.writeString(finalRuntime.resolve("bin"), "keep");

        new CleanupManagedPostgresWorkflow().cleanup(configuration);

        assertThat(download).doesNotExist();
        assertThat(staging).doesNotExist();
        assertThat(finalRuntime).exists();
    }

    @Test
    void cleanupRejectsTemporaryStorage() {
        final ManagedPostgresConfiguration configuration =
                CleanupWorkflowTestConfigurations.temporaryConfiguration(temporaryDirectory);

        assertThatExceptionOfType(PostgresCleanupException.class)
                .isThrownBy(() -> new CleanupManagedPostgresWorkflow().cleanup(configuration))
                .withMessage("Explicit cleanup is unsupported for temporary cluster storage");
    }

    private static ManagedPostgresConfiguration persistentConfiguration(
            final Path storageRoot, final CleanupPolicy cleanupPolicy, final RuntimeSource runtimeSource) {
        return CleanupWorkflowTestConfigurations.persistentConfiguration(storageRoot, cleanupPolicy, runtimeSource);
    }
}
