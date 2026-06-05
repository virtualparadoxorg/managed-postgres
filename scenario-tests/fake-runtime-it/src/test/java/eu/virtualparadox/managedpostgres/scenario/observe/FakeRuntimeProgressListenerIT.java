package eu.virtualparadox.managedpostgres.scenario.observe;

import static org.assertj.core.api.Assertions.assertThat;

import eu.virtualparadox.managedpostgres.PostgresStatus;
import eu.virtualparadox.managedpostgres.RunningPostgres;
import eu.virtualparadox.managedpostgres.config.RuntimeCache;
import eu.virtualparadox.managedpostgres.config.RuntimeRepository;
import eu.virtualparadox.managedpostgres.config.RuntimeSource;
import eu.virtualparadox.managedpostgres.observe.PostgresLogLevel;
import eu.virtualparadox.managedpostgres.observe.PostgresLogSource;
import eu.virtualparadox.managedpostgres.observe.StartupPhase;
import eu.virtualparadox.managedpostgres.observe.StartupProgress;
import eu.virtualparadox.managedpostgres.scenario.support.RecordingLogListener;
import eu.virtualparadox.managedpostgres.scenario.support.RecordingProgressListener;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioHttpArchiveServer;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioManagedPostgres;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioRuntimeArchives;
import eu.virtualparadox.managedpostgres.scenario.support.ScenarioShell;
import eu.virtualparadox.managedpostgres.test.FakePostgresRuntime;
import eu.virtualparadox.managedpostgres.test.FakePostgresScript;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the startup {@code onProgress} listener and the structured {@code logs().toListener(...)} listener over the
 * real fake-runtime download/start pipeline.
 */
final class FakeRuntimeProgressListenerIT {

    private static final String ARCHIVE_PATH = "/postgres-16.4.zip";
    private static final String READY_LINE = "database system is ready to accept connections";

    @TempDir
    private Path temporaryDirectory;

    FakeRuntimeProgressListenerIT() {}

    @Test
    void firstStartCacheMissEmitsFullDownloadPhaseSubsequenceWithByteProgress() throws IOException {
        final Path archive = fakeRuntimeArchive(ScenarioShell.recordingPgCtl(callLogPath()));
        final String checksumText = ScenarioRuntimeArchives.checksumText(archive);
        final Path cacheRoot = temporaryDirectory.resolve("runtime-cache");
        final Path storageRoot = temporaryDirectory.resolve("first-cluster");
        final RecordingProgressListener progress = new RecordingProgressListener();

        try (ScenarioHttpArchiveServer archiveServer = ScenarioHttpArchiveServer.serving(ARCHIVE_PATH, archive)) {
            final RuntimeSource runtimeSource = downloadedRuntimeSource(archiveServer.port(), cacheRoot, checksumText);

            try (RunningPostgres running = ScenarioManagedPostgres.localPostgres(
                            "downloaded-db", storageRoot, runtimeSource)
                    .onProgress(progress)
                    .start()) {
                assertThat(running.status()).isEqualTo(PostgresStatus.RUNNING);
            }
        }

        assertThat(progress.phases())
                .containsSubsequence(
                        StartupPhase.RESOLVING_RUNTIME,
                        StartupPhase.DOWNLOADING,
                        StartupPhase.VERIFYING,
                        StartupPhase.EXTRACTING,
                        StartupPhase.INITDB,
                        StartupPhase.STARTING,
                        StartupPhase.WAITING_FOR_READY,
                        StartupPhase.READY);
        assertThat(progress.events())
                .filteredOn(event -> event.phase() == StartupPhase.DOWNLOADING)
                .anySatisfy(FakeRuntimeProgressListenerIT::assertByteProgressFlowed);
    }

    @Test
    void secondStartCacheHitSkipsDownloadingAndExtracting() throws IOException {
        final Path archive = fakeRuntimeArchive(ScenarioShell.recordingPgCtl(callLogPath()));
        final String checksumText = ScenarioRuntimeArchives.checksumText(archive);
        final Path cacheRoot = temporaryDirectory.resolve("runtime-cache");
        final Path firstStorageRoot = temporaryDirectory.resolve("first-cluster");
        final Path secondStorageRoot = temporaryDirectory.resolve("second-cluster");

        try (ScenarioHttpArchiveServer archiveServer = ScenarioHttpArchiveServer.serving(ARCHIVE_PATH, archive)) {
            final RuntimeSource runtimeSource = downloadedRuntimeSource(archiveServer.port(), cacheRoot, checksumText);

            try (RunningPostgres warmUp = ScenarioManagedPostgres.localPostgres(
                            "downloaded-db", firstStorageRoot, runtimeSource)
                    .start()) {
                assertThat(warmUp.status()).isEqualTo(PostgresStatus.RUNNING);
            }

            final RecordingProgressListener progress = new RecordingProgressListener();
            try (RunningPostgres cached = ScenarioManagedPostgres.localPostgres(
                            "downloaded-db", secondStorageRoot, runtimeSource)
                    .onProgress(progress)
                    .start()) {
                assertThat(cached.status()).isEqualTo(PostgresStatus.RUNNING);

                assertThat(progress.phases())
                        .doesNotContain(StartupPhase.DOWNLOADING, StartupPhase.EXTRACTING)
                        .containsSubsequence(StartupPhase.RESOLVING_RUNTIME, StartupPhase.STARTING, StartupPhase.READY);
            }
        }
    }

    @Test
    void structuredServerLogLineReachesListenerWithSlf4jBridgeOff() throws IOException {
        final Path archive = fakeRuntimeArchive(ScenarioShell.loggingPgCtl(callLogPath()));
        final String checksumText = ScenarioRuntimeArchives.checksumText(archive);
        final Path cacheRoot = temporaryDirectory.resolve("runtime-cache");
        final Path storageRoot = temporaryDirectory.resolve("cluster");
        final RecordingLogListener logs = new RecordingLogListener();

        try (ScenarioHttpArchiveServer archiveServer = ScenarioHttpArchiveServer.serving(ARCHIVE_PATH, archive)) {
            final RuntimeSource runtimeSource = downloadedRuntimeSource(archiveServer.port(), cacheRoot, checksumText);

            try (RunningPostgres running = ScenarioManagedPostgres.localPostgres(
                            "downloaded-db", storageRoot, runtimeSource)
                    .logs()
                    .toListener(logs)
                    .start()) {
                assertThat(running.status()).isEqualTo(PostgresStatus.RUNNING);

                logs.awaitAtLeastOneLine(Duration.ofSeconds(5));
            }
        }

        assertThat(logs.lines()).anySatisfy(line -> {
            assertThat(line.level()).isEqualTo(PostgresLogLevel.LOG);
            assertThat(line.source()).isEqualTo(PostgresLogSource.SERVER);
            assertThat(line.message()).contains(READY_LINE);
        });
    }

    private static void assertByteProgressFlowed(final StartupProgress event) {
        assertThat(event.completedBytes()).isPositive();
        assertThat(event.percent()).isBetween(0, 100);
    }

    private Path callLogPath() {
        return temporaryDirectory.resolve("pg_ctl-calls.log");
    }

    private Path fakeRuntimeArchive(final FakePostgresScript pgCtl) throws IOException {
        return FakePostgresRuntime.create(temporaryDirectory.resolve("packaged-runtime"), pgCtl)
                .writeZipArchive(temporaryDirectory.resolve("repository").resolve("postgres-16.4.zip"));
    }

    private static RuntimeSource downloadedRuntimeSource(
            final int port, final Path cacheRoot, final String checksumText) {
        final URI archiveUri = URI.create("http://127.0.0.1:" + port + ARCHIVE_PATH);

        return RuntimeSource.downloaded(runtime -> runtime.repository(RuntimeRepository.custom(archiveUri))
                .cache(RuntimeCache.projectLocal(cacheRoot))
                .checksum(checksumText));
    }
}
