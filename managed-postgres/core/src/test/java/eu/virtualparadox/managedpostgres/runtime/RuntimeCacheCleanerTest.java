package eu.virtualparadox.managedpostgres.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.filesystem.ManagedPathOwnership;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheCleaner;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheLayout;
import eu.virtualparadox.managedpostgres.runtime.download.cleanup.RuntimeCacheRetention;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class RuntimeCacheCleanerTest {

    private static final Checksum CHECKSUM = Checksum.parse(
            "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    @TempDir
    private Path temporaryDirectory;

    RuntimeCacheCleanerTest() {
    }

    @Test
    void cleanerDeletesOnlyOwnedPartialDownloadFiles() throws IOException {
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(temporaryDirectory.resolve("cache"));
        final Path partialDownload = layout.downloadFile("16.4", CHECKSUM);
        final Path completedDownload = layout.downloadsDirectory().resolve("postgres-16.4.zip");
        final Path unrelatedDownload = layout.downloadsDirectory().resolve("manual.tmp");
        final Path rootDownload = layout.cacheRoot().resolve("postgres.zip.download");
        Files.createDirectories(layout.downloadsDirectory());
        Files.writeString(partialDownload, "partial");
        Files.writeString(completedDownload, "complete");
        Files.writeString(unrelatedDownload, "keep");
        Files.writeString(rootDownload, "keep");

        new RuntimeCacheCleaner().clean(layout);

        assertThat(partialDownload).doesNotExist();
        assertThat(completedDownload).exists();
        assertThat(unrelatedDownload).exists();
        assertThat(rootDownload).exists();
    }

    @Test
    void cleanerDeletesOnlyOwnedStagingDirectories() throws IOException {
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(temporaryDirectory.resolve("cache"));
        final ManagedPathOwnership ownership = new ManagedPathOwnership();
        final Path ownedStaging = layout.stagingDirectory("16.4", CHECKSUM);
        final Path unknownStaging = layout.runtimesDirectory().resolve("manual.staging");
        final Path publishedRuntime = layout.runtimeDirectory("16.4", CHECKSUM);
        Files.createDirectories(ownedStaging);
        Files.createDirectories(unknownStaging);
        Files.createDirectories(publishedRuntime);
        Files.writeString(ownedStaging.resolve("PG_VERSION"), "16");
        Files.writeString(unknownStaging.resolve("PG_VERSION"), "16");
        Files.writeString(publishedRuntime.resolve("PG_VERSION"), "16");
        ownership.writeMarker(ownedStaging, "install-runtime");

        new RuntimeCacheCleaner().clean(layout);

        assertThat(ownedStaging).doesNotExist();
        assertThat(unknownStaging).exists();
        assertThat(publishedRuntime).exists();
    }

    @Test
    void cleanerKeepsPathsWithCleanupSuffixButWrongFileType() throws IOException {
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(temporaryDirectory.resolve("cache"));
        final Path directoryNamedAsPartialDownload = layout.downloadsDirectory().resolve("manual.zip.download");
        final Path fileNamedAsStagingDirectory = layout.runtimesDirectory().resolve("manual.staging");
        Files.createDirectories(directoryNamedAsPartialDownload);
        Files.createDirectories(layout.runtimesDirectory());
        Files.writeString(fileNamedAsStagingDirectory, "manual");

        new RuntimeCacheCleaner().clean(layout);

        assertThat(directoryNamedAsPartialDownload).isDirectory();
        assertThat(fileNamedAsStagingDirectory).isRegularFile();
    }

    @Test
    void retainDeletesOnlyOwnedFinalRuntimeDirectoriesBeyondPolicy() throws IOException {
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(temporaryDirectory.resolve("cache"));
        final ManagedPathOwnership ownership = new ManagedPathOwnership();
        final Path currentRuntime = layout.runtimeDirectory("16.4", CHECKSUM);
        final Path oldRuntime = layout.runtimesDirectory().resolve("postgres-16.3-sha256-aaaaaaaaaaaa");
        final Path unknownRuntime = layout.runtimesDirectory().resolve("postgres-16.2-sha256-bbbbbbbbbbbb");
        Files.createDirectories(currentRuntime);
        Files.createDirectories(oldRuntime);
        Files.createDirectories(unknownRuntime);
        ownership.writeMarker(currentRuntime, "install-runtime");
        ownership.writeMarker(oldRuntime, "install-runtime");
        setModifiedTime(currentRuntime, "2026-05-28T00:00:00Z");
        setModifiedTime(oldRuntime, "2026-05-27T00:00:00Z");
        setModifiedTime(unknownRuntime, "2026-05-26T00:00:00Z");

        new RuntimeCacheRetention().retain(layout, currentRuntime, 1);

        assertThat(currentRuntime).isDirectory();
        assertThat(oldRuntime).doesNotExist();
        assertThat(unknownRuntime).isDirectory();
    }

    @Test
    void retainAlwaysKeepsCurrentRuntimeEvenWhenItIsOlderThanAnotherOwnedRuntime() throws IOException {
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(temporaryDirectory.resolve("cache"));
        final ManagedPathOwnership ownership = new ManagedPathOwnership();
        final Path currentRuntime = layout.runtimeDirectory("16.4", CHECKSUM);
        final Path newerRuntime = layout.runtimesDirectory().resolve("postgres-16.5-sha256-cccccccccccc");
        Files.createDirectories(currentRuntime);
        Files.createDirectories(newerRuntime);
        ownership.writeMarker(currentRuntime, "install-runtime");
        ownership.writeMarker(newerRuntime, "install-runtime");
        setModifiedTime(currentRuntime, "2026-05-27T00:00:00Z");
        setModifiedTime(newerRuntime, "2026-05-28T00:00:00Z");

        new RuntimeCacheRetention().retain(layout, currentRuntime, 1);

        assertThat(currentRuntime).isDirectory();
        assertThat(newerRuntime).doesNotExist();
    }

    private static void setModifiedTime(final Path directory, final String instant) throws IOException {
        Files.setLastModifiedTime(directory, FileTime.from(Instant.parse(instant)));
    }
}
