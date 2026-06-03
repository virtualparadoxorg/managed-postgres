package eu.virtualparadox.managedpostgres.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.runtime.Checksum;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheLayout;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioManagedPostgres;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioMetadata;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioRuntimeArchives;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioShell;
import eu.virtualparadox.managedpostgres.test.FakePostgresRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FakeRuntimeDownloadedRuntimeIT {

    private static final String POSTGRESQL_VERSION = "16.4";

    @TempDir
    private Path temporaryDirectory;

    FakeRuntimeDownloadedRuntimeIT() {}

    @Test
    void downloadedRuntimeArchiveInstallsStartsAndReusesCacheWhenRepositoryArtifactDisappears() throws IOException {
        final Path callLog = temporaryDirectory.resolve("pg_ctl-calls.log");
        final FakePostgresRuntime packagedRuntime = FakePostgresRuntime.create(
                temporaryDirectory.resolve("packaged-runtime"), ScenarioShell.recordingPgCtl(callLog));
        final Path archive = packagedRuntime.writeZipArchive(
                temporaryDirectory.resolve("repository").resolve("postgres-16.4.zip"));
        final String checksumText = ScenarioRuntimeArchives.checksumText(archive);
        final Path cacheRoot = temporaryDirectory.resolve("runtime-cache");
        final Path firstStorageRoot = temporaryDirectory.resolve("first-cluster");
        final Path secondStorageRoot = temporaryDirectory.resolve("second-cluster");
        final RuntimeSource runtimeSource = downloadedRuntimeSource(archive, cacheRoot, checksumText);
        final RuntimeCacheLayout cacheLayout = new RuntimeCacheLayout(cacheRoot);
        final Checksum checksum = Checksum.parse(checksumText);
        final Path cachedRuntime = cacheLayout.runtimeDirectory(POSTGRESQL_VERSION, checksum);

        try (RunningPostgres first = startLocalPostgres(firstStorageRoot, runtimeSource)) {
            assertThat(first.status()).isEqualTo(PostgresStatus.RUNNING);
            assertThat(ScenarioMetadata.metadataPath(firstStorageRoot)).isRegularFile();
            assertThat(cachedRuntime.resolve("bin").resolve("initdb")).satisfies(ScenarioRuntimeArchives::isExecutable);
            assertThat(cachedRuntime.resolve("bin").resolve("pg_isready"))
                    .satisfies(ScenarioRuntimeArchives::isExecutable);
        }

        Files.delete(archive);

        try (RunningPostgres second = startLocalPostgres(secondStorageRoot, runtimeSource)) {
            assertThat(second.status()).isEqualTo(PostgresStatus.RUNNING);
            assertThat(ScenarioMetadata.metadataPath(secondStorageRoot)).isRegularFile();
        }

        ScenarioRuntimeArchives.assertRuntimeCachePublished(
                cacheLayout, POSTGRESQL_VERSION, checksum, cachedRuntime, callLog);
    }

    private static RunningPostgres startLocalPostgres(final Path storageRoot, final RuntimeSource runtimeSource) {
        return ScenarioManagedPostgres.localPostgres("downloaded-db", storageRoot, runtimeSource)
                .start();
    }

    private static RuntimeSource downloadedRuntimeSource(
            final Path archive, final Path cacheRoot, final String checksumText) {
        return RuntimeSource.downloaded(runtime -> runtime.repository(RuntimeRepository.custom(archive.toUri()))
                .cache(RuntimeCache.projectLocal(cacheRoot))
                .checksum(checksumText));
    }
}
