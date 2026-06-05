package eu.virtualparadox.managedpostgres.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.virtualparadox.managedpostgres.ManagedPostgresException;
import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.filesystem.ManagedPathOwnership;
import eu.virtualparadox.managedpostgres.internal.runtime.ResolvedRuntime;
import eu.virtualparadox.managedpostgres.observe.ManagedPostgresProgressListener;
import eu.virtualparadox.managedpostgres.observe.StartupPhase;
import eu.virtualparadox.managedpostgres.observe.StartupProgress;
import eu.virtualparadox.managedpostgres.runtime.download.DownloadedRuntimeResolver;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeArtifactDownloader;
import eu.virtualparadox.managedpostgres.runtime.download.RuntimeCacheLayout;
import eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport;
import eu.virtualparadox.managedpostgres.runtime.testsupport.RuntimeArchiveTestSupport.EntrySpec;
import eu.virtualparadox.managedpostgres.runtime.testsupport.TarGzipArchiveTestSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings({
    // This suite exhaustively exercises the downloaded-runtime cache pipeline (cache hit/miss, telemetry,
    // signature, retention and progress phases), so it intentionally carries many small scenario methods.
    "PMD.CouplingBetweenObjects",
    "PMD.TooManyMethods"
})
public final class DownloadedRuntimeResolverTest {

    @TempDir
    private Path temporaryDirectory;

    DownloadedRuntimeResolverTest() {}

    @Test
    void cachedValidatedRuntimeIsReusedWithoutRedownload() throws IOException {
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final String checksumText = RuntimeArchiveTestSupport.checksumText(zipWithEntries(entry("unused", "unused")));
        final Checksum checksum = Checksum.parse(checksumText);
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path cachedRuntime = layout.runtimeDirectory("16.4", checksum);
        RuntimeArchiveTestSupport.createUsableRuntime(cachedRuntime);
        final RuntimeSource runtimeSource =
                downloadedSource(temporaryDirectory.resolve("missing.zip"), cacheRoot, checksumText);
        final RuntimeArtifactDownloader downloader = (repository, target, expectedChecksum) -> {
            throw new AssertionError("cached runtime must not trigger download");
        };

        final Path resolvedRuntime = new DownloadedRuntimeResolver(downloader).resolve(runtimeSource, "16.4");

        assertThat(resolvedRuntime).isEqualTo(cachedRuntime);
    }

    @Test
    void cachedValidatedRuntimeReportsZeroInstallDuration() throws IOException {
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final String checksumText = RuntimeArchiveTestSupport.checksumText(zipWithEntries(entry("unused", "unused")));
        final Checksum checksum = Checksum.parse(checksumText);
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path cachedRuntime = layout.runtimeDirectory("16.4", checksum);
        RuntimeArchiveTestSupport.createUsableRuntime(cachedRuntime);

        final ResolvedRuntime resolvedRuntime = new DownloadedRuntimeResolver()
                .resolveWithTelemetry(
                        downloadedSource(temporaryDirectory.resolve("unused.zip"), cacheRoot, checksumText), "16.4");

        assertThat(resolvedRuntime.runtimeDirectory()).isEqualTo(cachedRuntime);
        assertThat(resolvedRuntime.installDuration()).isZero();
    }

    @Test
    void cachedValidatedRuntimeIsReusedWithoutRepository() throws IOException {
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final String checksumText = RuntimeArchiveTestSupport.checksumText(zipWithEntries(entry("unused", "unused")));
        final Checksum checksum = Checksum.parse(checksumText);
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path cachedRuntime = layout.runtimeDirectory("16.4", checksum);
        RuntimeArchiveTestSupport.createUsableRuntime(cachedRuntime);
        final RuntimeSource runtimeSource = cacheOnlyDownloadedSource(cacheRoot, checksumText);
        final RuntimeArtifactDownloader downloader = (repository, target, expectedChecksum) -> {
            throw new AssertionError("cache-only downloaded runtime must not trigger download");
        };

        final Path resolvedRuntime = new DownloadedRuntimeResolver(downloader).resolve(runtimeSource, "16.4");

        assertThat(resolvedRuntime).isEqualTo(cachedRuntime);
    }

    @Test
    void cachedRuntimeResolutionAppliesRuntimeCacheRetention() throws IOException {
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final String checksumText = RuntimeArchiveTestSupport.checksumText(zipWithEntries(entry("unused", "unused")));
        final Checksum checksum = Checksum.parse(checksumText);
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path cachedRuntime = layout.runtimeDirectory("16.4", checksum);
        final Path oldRuntime = layout.runtimesDirectory().resolve("postgres-16.3-sha256-aaaaaaaaaaaa");
        final ManagedPathOwnership ownership = new ManagedPathOwnership();
        RuntimeArchiveTestSupport.createUsableRuntime(cachedRuntime);
        RuntimeArchiveTestSupport.createUsableRuntime(oldRuntime);
        ownership.writeMarker(cachedRuntime, "install-runtime");
        ownership.writeMarker(oldRuntime, "install-runtime");
        setModifiedTime(cachedRuntime, "2026-05-28T00:00:00Z");
        setModifiedTime(oldRuntime, "2026-05-27T00:00:00Z");
        final RuntimeSource runtimeSource = downloadedSource(
                temporaryDirectory.resolve("missing.zip"),
                RuntimeCache.projectLocal(cacheRoot).keepVersions(1),
                checksumText);
        final RuntimeArtifactDownloader downloader = (repository, target, expectedChecksum) -> {
            throw new AssertionError("cached runtime must not trigger download");
        };

        final Path resolvedRuntime = new DownloadedRuntimeResolver(downloader).resolve(runtimeSource, "16.4");

        assertThat(resolvedRuntime).isEqualTo(cachedRuntime);
        assertThat(cachedRuntime).isDirectory();
        assertThat(oldRuntime).doesNotExist();
    }

    @Test
    void missingCacheDownloadsVerifiesExtractsValidatesAndPublishesRuntime() throws IOException {
        final Path archive = zipWithEntries(
                entry("bin/pg_ctl", "pg_ctl"), entry("bin/psql", "psql"), entry("bin/postgres", "postgres"));
        final Path cacheRoot = temporaryDirectory.resolve("cache");

        assertDownloadedArchivePublishesRuntime(archive, cacheRoot);
    }

    @Test
    void missingCacheDownloadReportsPositiveInstallDuration() throws IOException {
        final Path archive = zipWithEntries(
                entry("bin/pg_ctl", "pg_ctl"), entry("bin/psql", "psql"), entry("bin/postgres", "postgres"));
        final Path cacheRoot = temporaryDirectory.resolve("cache");

        final ResolvedRuntime resolvedRuntime = new DownloadedRuntimeResolver()
                .resolveWithTelemetry(
                        downloadedSource(archive, cacheRoot, RuntimeArchiveTestSupport.checksumText(archive)), "16.4");

        assertThat(resolvedRuntime.runtimeDirectory()).isDirectory();
        assertThat(resolvedRuntime.installDuration()).isPositive();
    }

    @Test
    void missingCacheDownloadsTarGzipVerifiesExtractsValidatesAndPublishesRuntime() throws IOException {
        final Path archive = tarGzipWithEntries(
                entry("bin/pg_ctl", "pg_ctl"), entry("bin/psql", "psql"), entry("bin/postgres", "postgres"));
        final Path cacheRoot = temporaryDirectory.resolve("cache");

        assertDownloadedArchivePublishesRuntime(archive, cacheRoot);
    }

    @Test
    void missingCacheEmitsVerifyingAndExtractingPhasesOnPublish() throws IOException {
        final Path archive = zipWithEntries(
                entry("bin/pg_ctl", "pg_ctl"), entry("bin/psql", "psql"), entry("bin/postgres", "postgres"));
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final RecordingProgressListener listener = new RecordingProgressListener();

        new DownloadedRuntimeResolver()
                .resolveWithTelemetry(
                        downloadedSource(archive, cacheRoot, RuntimeArchiveTestSupport.checksumText(archive)),
                        "16.4",
                        listener);

        assertThat(listener.phases()).containsSequence(StartupPhase.VERIFYING, StartupPhase.EXTRACTING);
    }

    @Test
    void cachedRuntimeEmitsNoDownloadOrExtractEvents() throws IOException {
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final String checksumText = RuntimeArchiveTestSupport.checksumText(zipWithEntries(entry("unused", "unused")));
        final Checksum checksum = Checksum.parse(checksumText);
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path cachedRuntime = layout.runtimeDirectory("16.4", checksum);
        RuntimeArchiveTestSupport.createUsableRuntime(cachedRuntime);
        final RecordingProgressListener listener = new RecordingProgressListener();

        new DownloadedRuntimeResolver()
                .resolveWithTelemetry(
                        downloadedSource(temporaryDirectory.resolve("missing.zip"), cacheRoot, checksumText),
                        "16.4",
                        listener);

        assertThat(listener.phases())
                .doesNotContain(StartupPhase.DOWNLOADING, StartupPhase.VERIFYING, StartupPhase.EXTRACTING);
    }

    private void assertDownloadedArchivePublishesRuntime(final Path archive, final Path cacheRoot) throws IOException {
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Checksum checksum = Checksum.parse(checksumText);
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);
        final Path finalRuntime = layout.runtimeDirectory("16.4", checksum);

        final Path resolvedRuntime =
                new DownloadedRuntimeResolver().resolve(downloadedSource(archive, cacheRoot, checksumText), "16.4");

        assertThat(resolvedRuntime).isEqualTo(finalRuntime);
        assertThat(finalRuntime.resolve("bin").resolve("pg_ctl")).isRegularFile();
        assertThat(finalRuntime.resolve("bin").resolve("postgres")).isRegularFile();
        assertThat(layout.downloadFile("16.4", checksum)).doesNotExist();
        assertThat(layout.stagingDirectory("16.4", checksum)).doesNotExist();
    }

    @Test
    void failedChecksumLeavesNoFinalRuntime() throws IOException {
        final Path archive = zipWithEntries(
                entry("bin/pg_ctl", "pg_ctl"), entry("bin/psql", "psql"), entry("bin/postgres", "postgres"));
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final String wrongChecksumText = "sha256:0000000000000000000000000000000000000000000000000000000000000000";
        final Checksum wrongChecksum = Checksum.parse(wrongChecksumText);
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);

        assertThatThrownBy(() -> new DownloadedRuntimeResolver()
                        .resolve(downloadedSource(archive, cacheRoot, wrongChecksumText), "16.4"))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("failed to resolve")
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(layout.runtimeDirectory("16.4", wrongChecksum)).doesNotExist();
    }

    @Test
    void pathTraversalArchiveLeavesNoFinalRuntime() throws IOException {
        final Path archive = zipWithEntries(entry("../evil", "evil"));
        final String checksumText = RuntimeArchiveTestSupport.checksumText(archive);
        final Checksum checksum = Checksum.parse(checksumText);
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final RuntimeCacheLayout layout = new RuntimeCacheLayout(cacheRoot);

        assertThatThrownBy(() -> new DownloadedRuntimeResolver()
                        .resolve(downloadedSource(archive, cacheRoot, checksumText), "16.4"))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("failed to resolve")
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(layout.runtimeDirectory("16.4", checksum)).doesNotExist();
        assertThat(layout.stagingDirectory("16.4", checksum)).doesNotExist();
        assertThat(temporaryDirectory.resolve("evil")).doesNotExist();
    }

    @Test
    void defaultDownloadedSourceWithoutChecksumFailsWithClearDiagnostic() {
        assertThatThrownBy(() -> new DefaultRuntimeResolver().resolve(RuntimeSource.downloaded(), "16.4"))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("checksum")
                .satisfies(throwable -> assertThat(((ManagedPostgresException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("downloaded"));
    }

    @Test
    void cacheOnlyDownloadedSourceWithoutCachedRuntimeFailsWithClearDiagnostic() {
        final Path cacheRoot = temporaryDirectory.resolve("cache");
        final String checksumText = "sha256:1111111111111111111111111111111111111111111111111111111111111111";

        assertThatThrownBy(() -> new DefaultRuntimeResolver()
                        .resolve(cacheOnlyDownloadedSource(cacheRoot, checksumText), "16.4"))
                .isInstanceOf(ManagedPostgresException.class)
                .hasMessageContaining("repository")
                .satisfies(throwable -> assertThat(((ManagedPostgresException) throwable)
                                .diagnosticReport()
                                .renderText())
                        .contains("cached runtime is absent"));
    }

    private RuntimeSource downloadedSource(final Path archive, final Path cacheRoot, final String checksumText) {
        return downloadedSource(archive, RuntimeCache.projectLocal(cacheRoot), checksumText);
    }

    private RuntimeSource downloadedSource(
            final Path archive, final RuntimeCache runtimeCache, final String checksumText) {
        return RuntimeSource.downloaded(runtime -> runtime.repository(RuntimeRepository.custom(archive.toUri()))
                .cache(runtimeCache)
                .checksum(checksumText));
    }

    private RuntimeSource cacheOnlyDownloadedSource(final Path cacheRoot, final String checksumText) {
        return RuntimeSource.downloaded(
                runtime -> runtime.cache(RuntimeCache.projectLocal(cacheRoot)).checksum(checksumText));
    }

    private static void setModifiedTime(final Path directory, final String instant) throws IOException {
        Files.setLastModifiedTime(directory, FileTime.from(Instant.parse(instant)));
    }

    private Path zipWithEntries(final EntrySpec... entries) throws IOException {
        return RuntimeArchiveTestSupport.zipWithEntries(
                Files.createTempFile(temporaryDirectory, "runtime-", ".zip"), entries);
    }

    private Path tarGzipWithEntries(final EntrySpec... entries) throws IOException {
        return TarGzipArchiveTestSupport.tarGzipWithEntries(
                Files.createTempFile(temporaryDirectory, "runtime-", ".tar.gz"), entries);
    }

    private static EntrySpec entry(final String name, final String content) {
        return RuntimeArchiveTestSupport.entry(name, content);
    }

    private static final class RecordingProgressListener implements ManagedPostgresProgressListener {

        private final List<StartupPhase> phases = new ArrayList<>();

        @Override
        public void onProgress(final StartupProgress progress) {
            phases.add(progress.phase());
        }

        private List<StartupPhase> phases() {
            return List.copyOf(phases);
        }
    }
}
