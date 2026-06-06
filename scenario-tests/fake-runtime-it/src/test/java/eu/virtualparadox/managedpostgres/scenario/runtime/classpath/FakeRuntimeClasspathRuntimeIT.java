package eu.virtualparadox.managedpostgres.scenario.runtime.classpath;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.RuntimeCache;
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

final class FakeRuntimeClasspathRuntimeIT {

    private static final String POSTGRESQL_VERSION = "16.4";
    private static final String RESOURCE_NAME = "fake-postgres-classpath-runtime.zip";

    @TempDir
    private Path temporaryDirectory;

    FakeRuntimeClasspathRuntimeIT() {}

    @Test
    void classpathRuntimeArchiveInstallsStartsAndReusesCacheWhenResourceDisappears() throws IOException {
        final Path callLog = temporaryDirectory.resolve("pg_ctl-calls.log");
        final FakePostgresRuntime packagedRuntime = FakePostgresRuntime.create(
                temporaryDirectory.resolve("packaged-runtime"), ScenarioShell.recordingPgCtl(callLog));
        final Path resourceArchive = classpathResourceArchive();
        Files.deleteIfExists(resourceArchive);
        packagedRuntime.writeZipArchive(resourceArchive);
        final String checksumText = ScenarioRuntimeArchives.checksumText(resourceArchive);
        final Path cacheRoot = temporaryDirectory.resolve("runtime-cache");
        final RuntimeSource runtimeSource = classpathRuntimeSource(cacheRoot, checksumText);
        final RuntimeCacheLayout cacheLayout = new RuntimeCacheLayout(cacheRoot);
        final Checksum checksum = Checksum.parse(checksumText);
        final Path cachedRuntime = cacheLayout.runtimeDirectory(POSTGRESQL_VERSION, checksum);

        try {
            try (RunningPostgres first =
                    startLocalPostgres(temporaryDirectory.resolve("first-cluster"), runtimeSource)) {
                assertThat(first.status()).isEqualTo(PostgresStatus.RUNNING);
                assertThat(ScenarioMetadata.metadataPath(temporaryDirectory.resolve("first-cluster")))
                        .isRegularFile();
                assertThat(cachedRuntime.resolve("bin").resolve("pg_ctl"))
                        .satisfies(ScenarioRuntimeArchives::isExecutable);
            }

            Files.delete(resourceArchive);

            try (RunningPostgres second =
                    startLocalPostgres(temporaryDirectory.resolve("second-cluster"), runtimeSource)) {
                assertThat(second.status()).isEqualTo(PostgresStatus.RUNNING);
                assertThat(ScenarioMetadata.metadataPath(temporaryDirectory.resolve("second-cluster")))
                        .isRegularFile();
            }
        } finally {
            Files.deleteIfExists(resourceArchive);
        }

        ScenarioRuntimeArchives.assertRuntimeCachePublished(
                cacheLayout, POSTGRESQL_VERSION, checksum, cachedRuntime, callLog);
    }

    private static RunningPostgres startLocalPostgres(final Path storageRoot, final RuntimeSource runtimeSource) {
        return ScenarioManagedPostgres.localPostgres("classpath-db", storageRoot, runtimeSource)
                .start();
    }

    private static RuntimeSource classpathRuntimeSource(final Path cacheRoot, final String checksumText) {
        return RuntimeSource.classpath(RESOURCE_NAME, runtime -> runtime.cache(RuntimeCache.projectLocal(cacheRoot))
                .checksum(checksumText));
    }

    private static Path classpathResourceArchive() throws IOException {
        final Path archive = Path.of("target", "test-classes", RESOURCE_NAME).toAbsolutePath();
        final Path parent = archive.getParent();
        if (parent == null) {
            throw new IOException("classpath resource archive parent is unavailable: " + archive);
        }
        Files.createDirectories(parent);

        return archive;
    }
}
